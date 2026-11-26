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
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.cluster.SlotHash
import io.lettuce.core.internal.HostAndPort
import io.lettuce.core.pubsub.RedisPubSubAdapter
import io.lettuce.core.resource.ClientResources
import io.lettuce.core.resource.DnsResolvers
import io.lettuce.core.resource.MappingSocketAddressResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.store.ApplyResult
import me.ahoo.cosky.store.GrantResult
import me.ahoo.cosky.store.LeaseRenewal
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.NamespaceState
import me.ahoo.cosky.store.TerminateResult
import me.ahoo.cosky.store.key
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * What only a real multi-node Cluster can show: namespaces land in different slots on different nodes, and a hint
 * published on one node reaches a subscriber connected to another (design §6.1).
 */
class RedisStoreClusterTest {
    private fun namespaces(): Pair<String, String> {
        val nodeOf = { name: String -> SlotHash.getSlot("cosky:{$name}:rev") * NODES / SLOTS }
        val first = "ns-${UUID.randomUUID()}"
        val second = generateSequence { "ns-${UUID.randomUUID()}" }.first { nodeOf(it) != nodeOf(first) }
        return first to second
    }

    private fun config(ns: String, value: String) = resource {
        meta = objectMeta {
            namespace = ns
            name = "application.yaml"
        }
        config = configSpec { content = value }
    }

    @Test
    fun `a store opened on a cluster serves it, and leaves resources it was given open`() = runBlocking<Unit> {
        val (namespace, _) = namespaces()
        RedisStore.open("redis://127.0.0.1:${PORTS.first()}", cluster = true, resources = RESOURCES).use { store ->
            store.createNamespace(namespace)
            store.position(namespace).assert().isNotNull()
        }
        RESOURCES.eventExecutorGroup().isShuttingDown.assert().isFalse()
    }

    @Test
    fun `namespaces on different nodes work side by side`() = runBlocking<Unit> {
        val (first, second) = namespaces()
        val store = RedisStore.cluster(CLIENT)
        store.use {
            listOf(first, second).forEach { ns ->
                store.createNamespace(ns)
                store.apply(ns, listOf(Mutation.Put(config(ns, ns))), "test").assert()
                    .isEqualTo(ApplyResult.Applied(1, 1))
                store.readAll(ns).toList().single().config.content.assert().isEqualTo(ns)
            }
        }
    }

    /**
     * Until a message published on any node reaches a subscriber on every node. A cluster just created can lose one,
     * which then never arrives: Valkey 8 closes a cluster bus link, and drops the message, when the first message on it
     * is a PUBLISH, sent as a lightweight message before the receiving node has learnt whom the link comes from. A
     * store loses only a hint that way, and polls; this test, which polls once an hour, would wait for it.
     */
    private suspend fun awaitPubSubAcrossNodes() {
        val clients = PORTS.map { RedisClient.create(RESOURCES, RedisURI.create("127.0.0.1", it)) }
        try {
            // Which node's subscriber received a message published on which node.
            val received = ConcurrentHashMap.newKeySet<Pair<Int, String>>()
            PORTS.zip(clients) { port, client ->
                client.connectPubSub().apply {
                    addListener(
                        object : RedisPubSubAdapter<String, String>() {
                            override fun message(channel: String, message: String) {
                                received += port to message
                            }
                        },
                    )
                    async().subscribe(PROBE_CHANNEL).await()
                }
            }
            val publishers = clients.map { it.connect().async() }
            withTimeout(SETTLE_TIMEOUT) {
                while (received.size < PORTS.size * PORTS.size) {
                    PORTS.zip(publishers) { port, publisher -> publisher.publish(PROBE_CHANNEL, "$port") }
                        .forEach { it.await() }
                    delay(PROBE_INTERVAL)
                }
            }
        } finally {
            clients.forEach { it.shutdown() }
        }
    }

    @Test
    fun `hints reach followers of every node, without waiting for a poll`() = runBlocking<Unit> {
        awaitPubSubAcrossNodes()
        val (first, second) = namespaces()
        val store = RedisStore.cluster(CLIENT, tailPollInterval = 1.hours)
        store.use {
            withTimeout(30.seconds) {
                val followers = listOf(first, second).map { ns ->
                    val start = store.createNamespace(ns)
                    ns to async { store.changes(ns, start).first() }
                }
                delay(POLL_SETTLE)
                followers.forEach { (ns, _) -> store.apply(ns, listOf(Mutation.Put(config(ns, ns))), "test") }
                followers.forEach { (ns, follower) ->
                    follower.await().changes.single().resource.meta.namespace.assert().isEqualTo(ns)
                }
            }
        }
    }

    @Test
    fun `the prerequisites are checked on every node`() = runBlocking {
        RedisStore.cluster(CLIENT).use { it.checkPrerequisites() }
    }

    @Test
    fun `the whole lifecycle stays within a slot`() = runBlocking<Unit> {
        val (first, _) = namespaces()
        val store = RedisStore.cluster(CLIENT)
        store.use {
            store.createNamespace(first)
            store.apply(first, listOf(Mutation.Put(config(first, "1"))), "test")
            val lease = (store.grantLease(first, 1.minutes, "test") as GrantResult.Granted).lease.id
            store.keepAlive(first, listOf(lease)).assert().isEqualTo(mapOf(lease to LeaseRenewal(1.minutes, 0)))
            store.expireLease(first, lease, "test", force = true).assert().isTrue()
            store.apply(first, listOf(Mutation.Delete(config(first, "1").key)), "test")
            val epoch = checkNotNull(store.position(first)).epoch
            store.terminateNamespace(first, epoch).assert().isEqualTo(TerminateResult.TERMINATED)
            store.dropNamespace(first, epoch)
            store.namespaceState(first).assert().isEqualTo(NamespaceState.TERMINATING)
        }
    }

    private class ClusterContainer : GenericContainer<ClusterContainer>(REDIS_IMAGE)

    private companion object {
        const val NODES = 3
        const val SLOTS = SlotHash.SLOT_COUNT
        val POLL_SETTLE = 500.milliseconds
        val SETTLE_TIMEOUT = 30.seconds
        val PROBE_INTERVAL = 100.milliseconds
        const val PROBE_CHANNEL = "cosky-test:probe"
        val PORTS = listOf(7000, 7001, 7002)

        val CLUSTER: ClusterContainer = ClusterContainer()
            .withExposedPorts(*PORTS.toTypedArray())
            .withCommand(
                "sh",
                "-c",
                PORTS.joinToString(" ") { port -> "${clusterNode(port)} &&" } +
                    " sleep 1 && ${clusterCreate(PORTS)} && tail -f /dev/null",
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

        val CLIENT: RedisClusterClient = RedisClusterClient.create(
            RESOURCES,
            RedisURI.create("127.0.0.1", PORTS.first()),
        )

        /** Left running, the client would go on reading the topology, and starting threads, in the tests after. */
        @JvmStatic
        @AfterAll
        fun stop() {
            CLIENT.shutdown()
            RESOURCES.shutdown()
            CLUSTER.stop()
        }
    }
}
