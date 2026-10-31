/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.ahoo.cosky.migration

import io.lettuce.core.AbstractRedisClient
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.cluster.api.sync.RedisClusterCommands
import io.lettuce.core.resource.ClientResources
import java.time.Duration

internal data class SourceRole(
    val name: String,
    val description: String,
    /** Namespace to `r`, `w` or `rw`. */
    val grants: Map<String, String>,
)

internal data class SourceUser(val name: String, val roles: Set<String>)

/**
 * An instance that nothing renews, so nothing will register it again either. Metadata values may be private, and stay
 * out of the report.
 */
internal data class FixedInstance(
    val service: String,
    val id: String,
    val endpoint: String,
    val weight: String,
    val metadataKeys: List<String>,
)

/**
 * Reads what 6.x left in Redis, one key at a time, so that it works on a Redis Cluster as well. It only reads.
 *
 * Passwords stay out of this process: of the users it reads the names alone.
 */
internal class Source(private val redis: RedisClusterCommands<String, String>) {
    fun namespaces(): Set<String> = redis.smembers("$SYSTEM:ns_idx")

    fun configNames(namespace: String): List<String> =
        redis.smembers("$namespace:cfg_idx").map { it.removePrefix("$namespace:cfg:") }.sorted()

    /**
     * The contents 6.x still holds for one Config, the oldest first and the current one last. A content repeated in a
     * row, which removing a Config and setting it again leaves behind, is no change on 8.0.
     *
     * @return empty when the Config was removed in the meantime. One removed earlier is not in the index at all.
     */
    fun versions(namespace: String, name: String): List<String> {
        val current = redis.hget("$namespace:cfg:$name", "data") ?: return emptyList()
        val history = redis.zrange("$namespace:cfg_htr_idx:$name", 0, -1).mapNotNull { redis.hget(it, "data") }
        return history + current
    }

    fun services(namespace: String): List<String> = redis.smembers("$namespace:svc_idx").sorted()

    fun fixedInstances(namespace: String, service: String): List<FixedInstance> =
        redis.smembers("$namespace:svc_itc_idx:$service").sorted().mapNotNull { id ->
            val key = "$namespace:svc_itc:$id"
            if (redis.hget(key, "ephemeral") == "false") fixedInstance(service, id, redis.hgetall(key)) else null
        }

    private fun fixedInstance(service: String, id: String, fields: Map<String, String>) = FixedInstance(
        service = service,
        id = id,
        endpoint = "${fields["schema"]}://${fields["host"]}:${fields["port"]}",
        weight = fields["weight"].orEmpty(),
        metadataKeys = fields.keys.filter { it.startsWith(USER_METADATA) && !it.startsWith(SYSTEM_METADATA) }
            .map { it.removePrefix(USER_METADATA) }
            .sorted(),
    )

    fun roles(): List<SourceRole> = redis.hgetall("$SYSTEM:role_idx").entries.sortedBy { it.key }.map { (name, desc) ->
        SourceRole(name, desc, redis.hgetall("$SYSTEM:role_resource_bind:$name"))
    }

    fun users(): List<SourceUser> = redis.hkeys("$SYSTEM:user_idx").sorted().map { name ->
        SourceUser(name, redis.smembers("$SYSTEM:user_role_bind:$name"))
    }

    private companion object {
        const val SYSTEM = NamespaceMapping.SYSTEM
        const val USER_METADATA = "_"
        const val SYSTEM_METADATA = "__"
    }
}

/**
 * @property label where it reads from, without the password.
 */
internal class SourceConnection private constructor(
    val commands: RedisClusterCommands<String, String>,
    val label: String,
    private val shutdown: () -> Unit,
) : AutoCloseable {
    override fun close() = shutdown()

    companion object {
        private val QUIET = Duration.ZERO
        private val SHUTDOWN_TIMEOUT = Duration.ofSeconds(2)

        /**
         * @param resources shut down with the connection.
         */
        fun open(options: Options, resources: ClientResources = ClientResources.create()): SourceConnection {
            val uris = try {
                options.source.split(',').map(RedisURI::create)
            } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
                throw UsageException("The source is not a Redis URI: ${e.message}")
            }
            val connected: Pair<AbstractRedisClient, RedisClusterCommands<String, String>> = if (options.cluster) {
                RedisClusterClient.create(resources, uris).let { it to it.connect().sync() }
            } else {
                RedisClient.create(resources, uris.single()).let { it to it.connect().sync() }
            }
            val (client, commands) = connected
            return SourceConnection(commands, uris.joinToString { "${it.host}:${it.port}" }) {
                client.shutdown(QUIET, SHUTDOWN_TIMEOUT)
                resources.shutdown()
            }
        }
    }
}
