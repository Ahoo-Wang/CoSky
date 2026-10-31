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

import io.lettuce.core.cluster.api.sync.RedisClusterCommands

/**
 * Leaves in Redis what 6.x leaves there, following its scripts: `config_set.lua`, `config_remove.lua`,
 * `registry_register.lua`, `rbac_save_role.lua` and `user_bind_role.lua`.
 */
internal class SixRedis(private val redis: RedisClusterCommands<String, String>) {
    fun listNamespace(namespace: String) {
        redis.sadd("$SYSTEM:ns_idx", namespace)
    }

    fun setConfig(namespace: String, id: String, data: String) {
        val key = "$namespace:cfg:$id"
        redis.sadd("$namespace:cfg_idx", key)
        val current = redis.hget(key, "version")?.toInt()
        if (current != null) {
            archive(namespace, id, current, "set")
        }
        val archived = redis.zrevrangeWithScores("$namespace:cfg_htr_idx:$id", 0, 0).firstOrNull()?.score?.toInt()
        val last = current ?: archived
        redis.hset(
            key,
            mapOf(
                "configId" to id,
                "data" to data,
                "hash" to data.hashCode().toString(),
                "version" to ((last ?: 0) + 1).toString(),
                "createTime" to "1",
            ),
        )
    }

    fun removeConfig(namespace: String, id: String) {
        redis.srem("$namespace:cfg_idx", "$namespace:cfg:$id")
        archive(namespace, id, redis.hget("$namespace:cfg:$id", "version").toInt(), "remove")
    }

    private fun archive(namespace: String, id: String, version: Int, op: String) {
        val history = "$namespace:cfg_htr:$id:$version"
        redis.zadd("$namespace:cfg_htr_idx:$id", version.toDouble(), history)
        redis.rename("$namespace:cfg:$id", history)
        redis.hset(history, mapOf("op" to op, "opTime" to "1"))
    }

    fun register(
        namespace: String,
        service: String,
        id: String,
        ephemeral: Boolean = true,
        metadata: Map<String, String> = emptyMap(),
    ) {
        redis.sadd("$namespace:svc_itc_idx:$service", id)
        redis.sadd("$namespace:svc_idx", service)
        redis.hset(
            "$namespace:svc_itc:$id",
            mapOf(
                "instanceId" to id,
                "serviceId" to service,
                "schema" to "http",
                "host" to "10.0.0.1",
                "port" to "8080",
                "weight" to "2",
                "ephemeral" to ephemeral.toString(),
                "__system" to "internal",
            ) + metadata.mapKeys { "_${it.key}" },
        )
    }

    fun saveRole(name: String, description: String, grants: Map<String, String>) {
        redis.hset("$SYSTEM:role_idx", name, description)
        if (grants.isNotEmpty()) {
            redis.hset("$SYSTEM:role_resource_bind:$name", grants)
        }
    }

    fun addUser(name: String, passwordHash: String, vararg roles: String) {
        redis.hset("$SYSTEM:user_idx", name, passwordHash)
        if (roles.isNotEmpty()) {
            redis.sadd("$SYSTEM:user_role_bind:$name", *roles)
        }
    }

    private companion object {
        const val SYSTEM = NamespaceMapping.SYSTEM
    }
}
