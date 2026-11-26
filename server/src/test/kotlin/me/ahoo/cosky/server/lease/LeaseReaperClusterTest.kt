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

package me.ahoo.cosky.server.lease

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.api.v1.endpoint
import me.ahoo.cosky.api.v1.instanceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.server.observe.CoSkyMetrics
import me.ahoo.cosky.store.GrantResult
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.redis.RedisStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration
import java.time.Duration as JavaDuration

/**
 * The reaper on a Redis Cluster one of whose masters is down, with no replica to take over (design §6.2). A call that
 * waits out the store's 5-second command timeout is what the reaper takes for the store hanging as a whole, which
 * holds up every namespace; a call that fails at once holds up only its own. The namespaces on the other masters are
 * reaped while it is down.
 *
 * The cluster goes on serving the slots it still covers (`cluster-require-full-coverage no`), as one does until the
 * node timeout, by which time a failed master's replica usually takes over.
 */
class LeaseReaperClusterTest {
    private class ClusterContainer : GenericContainer<ClusterContainer>("redis:7.4-alpine")

    /**
     * Three masters, each in its own process in one container.
     *
     * @param nodeTimeout how long a node that does not answer takes for the others to agree it has failed.
     */
    private class Cluster(nodeTimeout: Duration) : AutoCloseable {
        val container: ClusterContainer = ClusterContainer()
            .withExposedPorts(*PORTS.toTypedArray())
            .withCommand(
                "sh",
                "-c",
                PORTS.joinToString(" ") { port ->
                    "redis-server --port $port --cluster-enabled yes --cluster-config-file n$port.conf " +
                        "--cluster-node-timeout ${nodeTimeout.inWholeMilliseconds} " +
                        "--cluster-require-full-coverage no --enable-debug-command yes " +
                        "--daemonize yes --save '' --appendonly no &&"
                } + " sleep 1 && redis-cli --cluster create " + PORTS.joinToString(" ") { "127.0.0.1:$it" } +
                    " --cluster-yes && echo CLUSTER_READY && tail -f /dev/null",
            )
            .waitingFor(Wait.forLogMessage(".*CLUSTER_READY.*", 1))
            .also { it.start() }

        /** Nodes the client finds nothing listening at, as on a host that is up when the node is not. */
        private val stopped = ConcurrentHashMap.newKeySet<Int>()
        private val nothingListening = ServerSocket(0).use { it.localPort }

        /** Nodes announce their ports inside the container, which Docker maps to others on the host. */
        val resources: ClientResources = ClientResources.builder()
            .socketAddressResolver(
                MappingSocketAddressResolver.create(DnsResolvers.UNRESOLVED) { address ->
                    if (address.port in stopped) {
                        HostAndPort.of(container.host, nothingListening)
                    } else {
                        HostAndPort.of(container.host, container.getMappedPort(address.port))
                    }
                },
            )
            .build()

        /**
         * A namespace on a master that holds neither the reaper's lock nor the slots the store starts from, which goes
         * down, and one on another master.
         */
        fun placement(): Placement {
            val inspector = RedisClusterClient.create(resources, RedisURI.create("127.0.0.1", PORTS.first()))
            try {
                inspector.refreshPartitions()
                val masterOf = { key: String -> inspector.partitions.getMasterBySlot(SlotHash.getSlot(key)).uri.port }
                val spared = setOf(PORTS.first(), masterOf("cosky:lock:${LeaseReaper.LOCK}"))
                val names = { generateSequence { "ns-${UUID.randomUUID()}" } }
                val stuck = names().first { masterOf("cosky:{$it}:rev") !in spared }
                val node = masterOf("cosky:{$stuck}:rev")
                return Placement(stuck, node, names().first { masterOf("cosky:{$it}:rev") != node })
            } finally {
                inspector.shutdown()
            }
        }

        suspend fun open(): Store =
            RedisStore.open("redis://127.0.0.1:${PORTS.first()}", cluster = true, resources = resources)

        /**
         * Stops a node, its connections refused from then on as on the host it ran on: Docker would go on accepting
         * them at the port it maps, and close them at once.
         */
        fun stop(port: Int) {
            val client = RedisClient.create(resources, RedisURI.create("127.0.0.1", port))
            val info = try {
                client.connect().use { it.sync().info("server") }
            } finally {
                client.shutdown()
            }
            val pid = info.lineSequence().first { it.startsWith("process_id:") }.substringAfter(':').trim()
            stopped += port
            container.execInContainer("kill", "-9", pid)
        }

        /**
         * Hangs a node: it answers nothing, its cluster bus included, and its connections stay open, as with a host
         * that vanished. Sent on a connection of its own, which the returned client holds until shut down.
         */
        fun hang(port: Int, hang: Duration): RedisClient =
            RedisClient.create(resources, RedisURI.create("127.0.0.1", port)).also {
                it.connect().async().dispatch(
                    CommandType.DEBUG,
                    StatusOutput(StringCodec.UTF8),
                    CommandArgs(StringCodec.UTF8).add("SLEEP").add(hang.inWholeSeconds),
                )
            }

        override fun close() {
            resources.shutdown()
            container.stop()
        }
    }

    private data class Placement(val stuck: String, val node: Int, val reaped: String)

    private suspend fun register(store: Store, namespace: String) {
        store.createNamespace(namespace)
        val lease = (store.grantLease(namespace, TTL, "test") as GrantResult.Granted).lease.id
        val instance = resource {
            meta = objectMeta {
                this.namespace = namespace
                name = "order-1"
                this.lease = lease
            }
            instance = instanceSpec {
                service = "order"
                endpoint = endpoint {
                    scheme = "http"
                    host = "10.0.0.1"
                    port = 8080
                }
            }
        }
        store.apply(namespace, listOf(Mutation.Put(instance)), "test")
    }

    /**
     * Registers a process in each namespace and lets its lease expire, takes the stuck namespace's master down, and
     * runs the reaper, the stuck namespace first: a call to it that waits out the timeout keeps the reaper from the
     * other. What happens then is up to [within].
     */
    private fun scenario(nodeTimeout: Duration, down: Cluster.(Int) -> AutoCloseable?, within: Duration) =
        runBlocking<Unit> {
            Cluster(nodeTimeout).use { cluster ->
                val placement = cluster.placement()
                cluster.open().use { store ->
                    val namespaces = listOf(placement.stuck, placement.reaped)
                    namespaces.forEach { register(store, it) }
                    // Expired a third of their TTL ago, so that the reaper may take them (design D32).
                    while (namespaces.any { store.expiredLeases(it, 1).isEmpty() }) {
                        delay(RETRY)
                    }
                    val registry = SimpleMeterRegistry()
                    val options = LeaseReaper.Options(grace = GRACE.toJavaDuration())
                    val reaper = LeaseReaper(store, { namespaces }, options, metrics = CoSkyMetrics(registry))
                    val holding = cluster.down(placement.node)
                    try {
                        val since = TimeSource.Monotonic.markNow()
                        while (store.readAll(placement.reaped).toList().isNotEmpty() && since.elapsedNow() < within) {
                            reaper.tick()
                            delay(options.interval.toKotlinDuration())
                        }

                        store.readAll(placement.reaped).toList().assert().isEmpty()
                        val failures = registry.find("cosky_reaper_failures_total").tag("namespace", placement.stuck)
                        (failures.counter()?.count() ?: 0.0).assert().isGreaterThan(0.0)
                    } finally {
                        holding?.close()
                    }
                }
            }
        }

    /**
     * Each call to it fails at once, since its connection is down. The cluster does not take it as failed within
     * this test: what happens comes from the connection alone.
     */
    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `a stopped master holds up reaping only in its own namespaces`() = scenario(
        nodeTimeout = 30.seconds,
        down = { node ->
            stop(node)
            null
        },
        within = STOPPED_REAPED_BY,
    )

    /**
     * Each call to it waits out the timeout, its connection open, until the cluster takes it as failed: the store then
     * leaves it out, and each call to it fails at once.
     */
    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `a hung master holds up reaping only in its own namespaces, once the cluster takes it as failed`() = scenario(
        nodeTimeout = 2.seconds,
        down = { node ->
            val hung = hang(node, HANG)
            AutoCloseable { hung.shutdown(JavaDuration.ZERO, JavaDuration.ZERO) }
        },
        within = HUNG_REAPED_BY,
    )

    private companion object {
        val PORTS = listOf(7400, 7401, 7402)
        val TTL = 1.seconds
        val GRACE = 1.seconds
        val RETRY = 200.milliseconds

        /** The grace after becoming leader, and a tick or two: a call that waited out the timeout would take longer. */
        val STOPPED_REAPED_BY = 10.seconds

        /**
         * The node timeout and the cluster agreeing, a read of the topology, which waits for the hung node up to the
         * command timeout, and the reaper's grace from the last call that waited: about 20 seconds at worst.
         */
        val HUNG_REAPED_BY = 35.seconds

        /** Longer than [HUNG_REAPED_BY], so that the namespaces on the other masters are reaped while it hangs. */
        val HANG = 50.seconds
    }
}
