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

package me.ahoo.cosky.store.redis

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.sync.RedisCommands
import io.lettuce.core.internal.HostAndPort
import io.lettuce.core.resource.ClientResources
import io.lettuce.core.resource.DnsResolvers
import io.lettuce.core.resource.MappingSocketAddressResolver
import kotlinx.coroutines.delay
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import kotlin.time.Duration.Companion.milliseconds

/**
 * A master, its replica and three Sentinels watching them, all in one container. Each announces its port inside the
 * container, which [resources] map to the one Docker gives it on the host.
 */
internal class SentinelRedis : AutoCloseable {
    private class Container : GenericContainer<Container>(REDIS_IMAGE)

    private val container: Container = Container()
        .withExposedPorts(MASTER, REPLICA, *SENTINELS.toTypedArray())
        .withCommand(
            "sh",
            "-c",
            "redis-server --port $MASTER --enable-debug-command yes --daemonize yes --save '' --appendonly no && " +
                "redis-server --port $REPLICA --replicaof 127.0.0.1 $MASTER --daemonize yes --save '' " +
                "--appendonly no && " +
                SENTINELS.joinToString(" ") { port ->
                    "printf 'sentinel monitor $NAME 127.0.0.1 $MASTER 2\\n" +
                        "sentinel down-after-milliseconds $NAME 2000\\n" +
                        "sentinel failover-timeout $NAME 10000\\n' > s$port.conf && " +
                        "redis-server s$port.conf --sentinel --port $port --daemonize yes &&"
                } + " echo SENTINELS_READY && tail -f /dev/null",
        )
        .waitingFor(Wait.forLogMessage(".*SENTINELS_READY.*", 1))
        .also { it.start() }

    val resources: ClientResources = ClientResources.builder()
        .socketAddressResolver(
            MappingSocketAddressResolver.create(DnsResolvers.UNRESOLVED) { address ->
                HostAndPort.of(container.host, container.getMappedPort(address.port))
            },
        )
        .build()

    val uri: String = "redis-sentinel://" + SENTINELS.joinToString(",") { "127.0.0.1:$it" } + "#$NAME"

    fun <T> node(port: Int, block: RedisCommands<String, String>.() -> T): T {
        val client = RedisClient.create(resources, RedisURI.create("127.0.0.1", port))
        try {
            return client.connect().use { it.sync().block() }
        } finally {
            client.shutdown()
        }
    }

    /** Until the replica has synced and each Sentinel has heard of the others, so that enough of them can agree. */
    suspend fun awaitReady() {
        while ("master_link_status:up" !in node(REPLICA) { info("replication") } ||
            !SENTINELS.all { port -> "sentinels=${SENTINELS.size}" in node(port) { info("sentinel") } }
        ) {
            delay(RETRY)
        }
    }

    override fun close() {
        resources.shutdown()
        container.stop()
    }

    companion object {
        const val MASTER = 7200
        const val REPLICA = 7201
        val SENTINELS = listOf(26400, 26401, 26402)
        const val NAME = "cosky"
        private val RETRY = 200.milliseconds
    }
}
