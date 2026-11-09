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
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.cluster.SlotHash
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.internal.HostAndPort
import io.lettuce.core.output.StatusOutput
import io.lettuce.core.protocol.CommandArgs
import io.lettuce.core.protocol.CommandType
import io.lettuce.core.resource.ClientResources
import io.lettuce.core.resource.DnsResolvers
import io.lettuce.core.resource.MappingSocketAddressResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.store.ApplyResult
import me.ahoo.cosky.store.Mutation
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * A master that fails is replaced by its replica within the cluster's node timeout, and the store follows it: the
 * namespaces in its slots take writes again within seconds, rather than once the old node answers again.
 */
class RedisStoreClusterFailoverTest {
    private class ClusterContainer : GenericContainer<ClusterContainer>(REDIS_IMAGE)

    private fun config(namespace: String, content: String) = resource {
        meta = objectMeta {
            this.namespace = namespace
            name = "application.yaml"
        }
        config = configSpec { this.content = content }
    }

    private fun <T> node(port: Int, block: RedisCommands<String, String>.() -> T): T {
        val client = RedisClient.create(RESOURCES, RedisURI.create("127.0.0.1", port))
        try {
            return client.connect().use { it.sync().block() }
        } finally {
            client.shutdown()
        }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `the namespaces of a hung master take writes again once its replica takes over`() = runBlocking<Unit> {
        val inspector = RedisClusterClient.create(RESOURCES, RedisURI.create("127.0.0.1", PORTS.first()))
        try {
            inspector.refreshPartitions()
            // A master other than the one the store is given to start from.
            val namespace = generateSequence { "ns-${UUID.randomUUID()}" }.first {
                inspector.partitions.getMasterBySlot(SlotHash.getSlot("cosky:{$it}:rev")).uri.port != PORTS.first()
            }
            val master = inspector.partitions.getMasterBySlot(SlotHash.getSlot("cosky:{$namespace}:rev")).uri.port
            RedisStore.open("redis://127.0.0.1:${PORTS.first()}", cluster = true, resources = RESOURCES).use { store ->
                store.createNamespace(namespace)
                store.apply(namespace, listOf(Mutation.Put(config(namespace, "before"))), "test")

                // A replica that has not synced yet counts its data as too old to take over.
                while (PORTS.any {
                        node(
                            it
                        ) { "role:slave" in info("replication") && "master_link_status:up" !in info("replication") }
                    }
                ) {
                    delay(RETRY)
                }
                // Hung rather than gone: it answers nothing, its cluster bus included, and its connections stay open,
                // as with a host that vanished. Sent on a connection of its own, which this leaves waiting.
                val hung = RedisClient.create(RESOURCES, RedisURI.create("127.0.0.1", master))
                hung.connect().async().dispatch(
                    CommandType.DEBUG,
                    StatusOutput(StringCodec.UTF8),
                    CommandArgs(StringCodec.UTF8).add("SLEEP").add(HANG.inWholeSeconds),
                )
                val failed = TimeSource.Monotonic.markNow()
                var applied = false
                while (!applied && failed.elapsedNow() < RECOVERY) {
                    applied = withTimeoutOrNull(ATTEMPT) {
                        runCatching { store.apply(namespace, listOf(Mutation.Put(config(namespace, "after"))), "test") }
                            .getOrNull()
                    } is ApplyResult.Applied
                    if (!applied) {
                        delay(RETRY)
                    }
                }

                applied.assert().isTrue()
                hung.shutdown(Duration.ZERO, Duration.ZERO)
            }
        } finally {
            inspector.shutdown()
        }
    }

    private companion object {
        /**
         * The node timeout, a replica's election, and two periodic reads of the topology with the wait for the hung node
         * within each, should the first begin before the election: about 25 seconds at worst. Without reading the
         * topology again, never, while the node hangs.
         */
        val RECOVERY = 30.seconds
        val ATTEMPT = 3.seconds
        val RETRY = 200.milliseconds

        /** Longer than [RECOVERY], so that only following the failover can make the store take writes again. */
        val HANG = 60.seconds
        val PORTS = (7100..7105).toList()

        val CLUSTER: ClusterContainer = ClusterContainer()
            .withExposedPorts(*PORTS.toTypedArray())
            .withCommand(
                "sh",
                "-c",
                PORTS.joinToString(" ") { port ->
                    "redis-server --port $port --cluster-enabled yes --cluster-config-file n$port.conf " +
                        "--cluster-node-timeout 2000 --enable-debug-command yes --daemonize yes --save '' " +
                        "--appendonly no &&"
                } + " sleep 1 && redis-cli --cluster create " + PORTS.joinToString(" ") { "127.0.0.1:$it" } +
                    " --cluster-replicas 1 --cluster-yes && echo CLUSTER_READY && tail -f /dev/null",
            )
            .waitingFor(Wait.forLogMessage(".*CLUSTER_READY.*", 1))
            .also { it.start() }

        /** Nodes announce their ports inside the container, which Docker maps to others on the host. */
        val RESOURCES: ClientResources = ClientResources.builder()
            .socketAddressResolver(
                MappingSocketAddressResolver.create(DnsResolvers.UNRESOLVED) { address ->
                    HostAndPort.of(CLUSTER.host, CLUSTER.getMappedPort(address.port))
                },
            )
            .build()

        @JvmStatic
        @AfterAll
        fun stop() {
            RESOURCES.shutdown()
            CLUSTER.stop()
        }
    }
}
