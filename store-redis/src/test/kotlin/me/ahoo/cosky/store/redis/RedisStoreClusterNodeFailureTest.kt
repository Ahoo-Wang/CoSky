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
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.store.ApplyResult
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.StoreUnavailableException
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
import java.time.Duration as JavaDuration

/**
 * A node of a Redis Cluster that is down, with no replica to take over: writes to the namespaces in its slots fail at
 * once, rather than each waiting out the command timeout, and the other nodes are served all along. The reaper takes a
 * wait as long as that timeout for the store hanging as a whole, and pauses every namespace, where a failure that comes
 * at once pauses only its own (design §6.2).
 *
 * The cluster goes on serving the slots it still covers (`cluster-require-full-coverage no`), as one does until the
 * node timeout, by which time a failed master's replica usually takes over.
 */
class RedisStoreClusterNodeFailureTest {
    private class ClusterContainer : GenericContainer<ClusterContainer>(REDIS_IMAGE)

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
                    clusterNode(
                        port,
                        "--cluster-node-timeout ${nodeTimeout.inWholeMilliseconds} " +
                            "--cluster-require-full-coverage no --enable-debug-command yes",
                    ) + " &&"
                } + " sleep 1 && ${clusterCreate(PORTS)} && tail -f /dev/null",
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
         * Stops a node. Docker would go on accepting connections to the port it maps, and close them at once, which
         * the client takes for the node failing as it answers: it would fail every command at once by itself. A
         * connection refused, as on the host the node ran on, makes it wait for the node to come back instead.
         */
        fun stop(port: Int) {
            val pid = node(port) { info("server") }
                .lineSequence().first { it.startsWith("process_id:") }.substringAfter(':').trim()
            stopped += port
            container.execInContainer("kill", "-9", pid)
        }

        /**
         * A namespace on a master other than the one the store starts from, which goes down, and one on another.
         */
        fun placement(): Placement {
            val inspector = RedisClusterClient.create(resources, RedisURI.create("127.0.0.1", PORTS.first()))
            try {
                inspector.refreshPartitions()
                val masterOf = { namespace: String ->
                    inspector.partitions.getMasterBySlot(SlotHash.getSlot("cosky:{$namespace}:rev")).uri.port
                }
                val names = { generateSequence { "ns-${UUID.randomUUID()}" } }
                val lost = names().first { masterOf(it) != PORTS.first() }
                return Placement(lost, masterOf(lost), names().first { masterOf(it) != masterOf(lost) })
            } finally {
                inspector.shutdown()
            }
        }

        suspend fun open(): Store =
            RedisStore.open("redis://127.0.0.1:${PORTS.first()}", cluster = true, resources = resources)

        fun <T> node(port: Int, block: RedisCommands<String, String>.() -> T): T {
            val client = RedisClient.create(resources, RedisURI.create("127.0.0.1", port))
            try {
                return client.connect().use { it.sync().block() }
            } finally {
                client.shutdown()
            }
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

    private data class Placement(val lost: String, val node: Int, val kept: String)

    private data class Attempt(val result: Result<ApplyResult>, val took: Duration) {
        val failedAtOnce: Boolean
            get() = result.exceptionOrNull() is StoreUnavailableException && took < AT_ONCE
    }

    private suspend fun Store.write(namespace: String): Attempt {
        val config = resource {
            meta = objectMeta {
                this.namespace = namespace
                name = "application.yaml"
            }
            config = configSpec { content = UUID.randomUUID().toString() }
        }
        val started = TimeSource.Monotonic.markNow()
        val result = runCatching { apply(namespace, listOf(Mutation.Put(config)), "test") }
        return Attempt(result, started.elapsedNow())
    }

    private suspend fun Store.assertServed(namespace: String) {
        write(namespace).result.getOrThrow().assert().isInstanceOf(ApplyResult.Applied::class.java)
    }

    private fun Attempt.assertFailedAtOnce() {
        result.exceptionOrNull().assert().isInstanceOf(StoreUnavailableException::class.java)
        took.inWholeMilliseconds.assert().isLessThan(AT_ONCE.inWholeMilliseconds)
    }

    /**
     * Its connection drops, and every attempt to connect again is refused. The cluster does not take it as failed
     * within this test: what follows comes from the connection alone.
     */
    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `the namespaces of a stopped node fail at once, and those of the others are served`() = runBlocking<Unit> {
        Cluster(nodeTimeout = 30.seconds).use { cluster ->
            val placement = cluster.placement()
            cluster.open().use { store ->
                store.createNamespace(placement.lost)
                store.createNamespace(placement.kept)
                cluster.stop(placement.node)
                // Time for the store to see the connection drop: a write on its way then waits out the timeout.
                delay(NOTICED)
                val stopped = TimeSource.Monotonic.markNow()
                while (stopped.elapsedNow() < OBSERVED) {
                    store.write(placement.lost).assertFailedAtOnce()
                    store.assertServed(placement.kept)
                    delay(RETRY)
                }
            }
        }
    }

    /**
     * Its connections stay open, and each write waits out the command timeout until the cluster takes it as failed:
     * then the store, having read the topology again, finds no node for its slots, until it answers again.
     */
    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `the namespaces of a hung node fail at once once the cluster takes it as failed, until it answers`() =
        runBlocking<Unit> {
            Cluster(nodeTimeout = 2.seconds).use { cluster ->
                val placement = cluster.placement()
                cluster.open().use { store ->
                    store.createNamespace(placement.lost)
                    store.createNamespace(placement.kept)
                    val hung = cluster.hang(placement.node, HANG)
                    try {
                        val since = TimeSource.Monotonic.markNow()
                        var attempt = store.write(placement.lost)
                        while (!attempt.failedAtOnce && since.elapsedNow() < FAILED_BY) {
                            store.assertServed(placement.kept)
                            attempt = store.write(placement.lost)
                        }
                        attempt.assertFailedAtOnce()
                        repeat(ATTEMPTS) {
                            store.write(placement.lost).assertFailedAtOnce()
                            store.assertServed(placement.kept)
                            delay(RETRY)
                        }
                        while (since.elapsedNow() < HANG) {
                            delay(RETRY)
                        }
                        var back = store.write(placement.lost)
                        while (back.result.getOrNull() !is ApplyResult.Applied && since.elapsedNow() < HANG + BACK_BY) {
                            delay(RETRY)
                            back = store.write(placement.lost)
                        }
                        back.result.getOrThrow().assert().isInstanceOf(ApplyResult.Applied::class.java)
                    } finally {
                        hung.shutdown(JavaDuration.ZERO, JavaDuration.ZERO)
                    }
                }
            }
        }

    private companion object {
        val PORTS = listOf(7300, 7301, 7302)

        /** Well short of the command timeout, which is what a write to a node that does not answer waits for. */
        val AT_ONCE = 1.seconds
        val RETRY = 200.milliseconds
        const val ATTEMPTS = 3

        val NOTICED = 500.milliseconds

        /** Longer than the command timeout, and than the store takes to read the topology again. */
        val OBSERVED = 8.seconds

        /**
         * The node timeout and the cluster agreeing, then a periodic read of the topology, which waits for the hung
         * node up to the command timeout: about 10 seconds at worst, and each write before waits that long too.
         */
        val FAILED_BY = 20.seconds

        /** Longer than [FAILED_BY], so that only leaving the node out can make writes fail at once. */
        val HANG = 30.seconds

        /** The others take it back at once, and a write for a slot with no node has the store read the topology. */
        val BACK_BY = 20.seconds
    }
}
