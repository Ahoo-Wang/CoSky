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

package me.ahoo.cosky.server.fault

import eu.rekawek.toxiproxy.Proxy
import eu.rekawek.toxiproxy.ToxiproxyClient
import eu.rekawek.toxiproxy.model.ToxicDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.v1.DeleteRequest
import me.ahoo.cosky.api.v1.Endpoint
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.ListRequest
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.WatchResponse
import me.ahoo.cosky.api.v1.getRequest
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.client.CoSkyClient
import me.ahoo.cosky.client.CoSkyClientOptions
import me.ahoo.cosky.server.RedisContainer
import me.ahoo.cosky.server.RunningServer
import me.ahoo.cosky.store.key
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.testcontainers.Testcontainers
import org.testcontainers.containers.Network
import org.testcontainers.toxiproxy.ToxiproxyContainer
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The failures design §7.11 lists, on real Redis, with Toxiproxy between the parties. What must hold through them: live
 * instances are not deleted, and no client is left on a view that is no longer true.
 */
class FaultInjectionTest {
    private val id = UUID.randomUUID().toString().take(8)
    private val redisAlias = "redis-$id"
    private val redis = RedisContainer("redis-server", "--appendonly", "yes")
        .withNetwork(NETWORK)
        .withNetworkAliases(redisAlias)
        .also { it.start() }
    private val redisProxy = proxy("redis-$id", "$redisAlias:${RedisContainer.REDIS_PORT}")
    private val serverPort = ServerSocket(0).use { it.localPort }
    private val clients = mutableListOf<CoSkyClient>()
    private var server: RunningServer? = null

    private val running: RunningServer
        get() = checkNotNull(server)

    private fun proxy(name: String, upstream: String): Proxy {
        val port = TOXIPROXY_FIRST_PORT + NEXT_PROXY.getAndIncrement()
        return TOXIPROXY_CLIENT.createProxy(name, "0.0.0.0:$port", upstream)
    }

    private fun mappedPort(proxy: Proxy): Int = TOXIPROXY.getMappedPort(proxy.listen.substringAfterLast(':').toInt())

    private fun properties(grace: Duration, protectionMinLeases: Int): Map<String, Any> = mapOf(
        "spring.main.web-application-type" to "none",
        "cosky.store.redis.uri" to "redis://${TOXIPROXY.host}:${mappedPort(redisProxy)}",
        "cosky.store.redis.tail-poll-interval" to "100ms",
        "cosky.lease.reaper-interval" to "200ms",
        "cosky.lease.reaper-grace" to "${grace.inWholeMilliseconds}ms",
        "cosky.lease.protection-min-leases" to protectionMinLeases,
        "cosky.lease.lock-ttl" to "500ms",
        "cosky.auth.enabled" to "false",
    )

    private fun startServer(grace: Duration = GRACE, protectionMinLeases: Int = NO_PROTECTION): RunningServer =
        RunningServer(properties(grace, protectionMinLeases), port = serverPort).also { server = it }

    private fun client(endpoint: String = "localhost:$serverPort"): CoSkyClient = CoSkyClient(
        CoSkyClientOptions(
            endpoints = listOf(endpoint),
            snapshotDir = null,
            reconnectMin = 50.milliseconds,
            reconnectMax = 500.milliseconds,
            reRegisterJitter = Duration.ZERO,
            leaseTtl = LEASE_TTL,
        ),
    ).also { clients += it }

    /**
     * A client that reaches the server through a proxy, which a test can then cut.
     */
    private fun clientBehindProxy(): Pair<CoSkyClient, Proxy> {
        Testcontainers.exposeHostPorts(serverPort)
        val proxy = proxy("server-$id-${clients.size}", "host.testcontainers.internal:$serverPort")
        return client("${TOXIPROXY.host}:${mappedPort(proxy)}") to proxy
    }

    private fun scenario(block: suspend CoroutineScope.() -> Unit): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(SCENARIO_TIMEOUT) {
            block()
        }
    }

    private suspend fun instances(): List<Resource> = running.resources.list(
        ListRequest.newBuilder().setNamespace(NAMESPACE).setKind(Kind.KIND_INSTANCE).build(),
    ).itemsList

    private suspend fun eventually(condition: suspend () -> Boolean) {
        while (!runCatching { condition() }.getOrDefault(false)) {
            delay(POLL)
        }
    }

    private fun deletions(seen: List<WatchResponse>): List<String> = seen.filter { it.hasEvents() }
        .flatMap { it.events.eventsList }
        .filter { it.type == WatchResponse.Event.Type.TYPE_DELETE }
        .map { it.resource.meta.name }

    private fun CoroutineScope.observe(seen: MutableList<WatchResponse>) =
        launch { running.watch(NAMESPACE, Kind.KIND_INSTANCE, seen).toList() }

    @AfterEach
    fun stop() {
        clients.forEach { it.close() }
        server?.close()
        redisProxy.delete()
        redis.stop()
    }

    // --- Redis ---

    @Test
    fun `redis gone for longer than the ttl deletes no live instance`() = scenario {
        startServer().createNamespace(NAMESPACE)
        delay(PAST_GRACE)
        val (client, clientProxy) = clientBehindProxy()
        client.namespace(NAMESPACE).instances.register("order", ENDPOINT, instanceName = "order-1")
        val lease = instances().single().meta.lease
        val seen = CopyOnWriteArrayList<WatchResponse>()
        val observer = observe(seen)
        delay(SETTLE)

        // A renewal must not be waiting at Redis, or it would run first on recovery and save the lease by itself.
        clientProxy.disable()
        delay(SETTLE)
        redisProxy.disable()
        delay(LONGER_THAN_TTL)
        redisProxy.enable()
        // The client only comes back a little later, as a reconnect backoff does: this is what the grace is for.
        delay(WITHIN_GRACE)
        clientProxy.enable()
        delay(PAST_GRACE)

        deletions(seen).assert().isEmpty()
        eventually { instances().singleOrNull()?.meta?.lease == lease }
        observer.cancel()
    }

    @Test
    fun `redis that hangs for longer than the ttl deletes no live instance`() = scenario {
        startServer().createNamespace(NAMESPACE)
        delay(PAST_GRACE)
        val (client, clientProxy) = clientBehindProxy()
        client.namespace(NAMESPACE).instances.register("order", ENDPOINT, instanceName = "order-1")
        val lease = instances().single().meta.lease
        val seen = CopyOnWriteArrayList<WatchResponse>()
        val observer = observe(seen)
        delay(SETTLE)

        clientProxy.disable()
        delay(SETTLE)
        // Nothing fails: commands simply get no answer, as when Redis is paused or the network drops packets.
        val down = redisProxy.toxics().timeout("down", ToxicDirection.DOWNSTREAM, 0)
        val up = redisProxy.toxics().timeout("up", ToxicDirection.UPSTREAM, 0)
        delay(LONGER_THAN_TTL)
        down.remove()
        up.remove()
        delay(WITHIN_GRACE)
        clientProxy.enable()
        delay(PAST_GRACE)

        deletions(seen).assert().isEmpty()
        eventually { instances().singleOrNull()?.meta?.lease == lease }
        observer.cancel()
    }

    @Test
    fun `a restarted redis keeps what it persisted, and everything carries on`() = scenario {
        startServer().createNamespace(NAMESPACE)
        val instances = client().namespace(NAMESPACE).instances
        instances.register("order", ENDPOINT, instanceName = "order-1")
        val lease = instances().single().meta.lease
        val view = client().namespace(NAMESPACE).configs.watch("a")
        running.putConfig(NAMESPACE, "a", "before")
        view.first { it?.config?.content == "before" }

        redis.dockerClient.restartContainerCmd(redis.containerId).exec()

        eventually {
            running.putConfig(NAMESPACE, "a", "after")
            true
        }
        view.first { it?.config?.content == "after" }
        eventually { instances().singleOrNull()?.meta?.lease == lease }
    }

    // --- servers ---

    @Test
    fun `every server gone for longer than the ttl deletes no live instance`() = scenario {
        startServer().createNamespace(NAMESPACE)
        val (client, clientProxy) = clientBehindProxy()
        client.namespace(NAMESPACE).instances.register("order", ENDPOINT, instanceName = "order-1")
        val before = instances().single()
        val position = running.watch(NAMESPACE, Kind.KIND_INSTANCE).first().snapshot

        clientProxy.disable()
        running.close()
        delay(LONGER_THAN_TTL)
        startServer()
        // The new server leads the reaper by now, and the client is not back yet.
        delay(WITHIN_GRACE)
        clientProxy.enable()
        delay(PAST_GRACE)

        val seen = CopyOnWriteArrayList<WatchResponse>()
        val resumed = launch {
            running.watch(
                NAMESPACE,
                Kind.KIND_INSTANCE,
                seen,
                fromRevision = position.revision,
                fromEpoch = position.epoch,
            ).toList()
        }
        delay(SETTLE)
        deletions(seen).assert().isEmpty()
        seen.none { it.hasGone() }.assert().isTrue()
        instances().single().meta.lease.assert().isEqualTo(before.meta.lease)
        resumed.cancel()
    }

    @Test
    fun `with one of two servers killed, a client carries on with the other`() = scenario {
        val secondPort = ServerSocket(0).use { it.localPort }
        val first = startServer()
        first.createNamespace(NAMESPACE)
        val second = RunningServer(properties(GRACE, NO_PROTECTION), port = secondPort)
        try {
            val client = CoSkyClient(
                CoSkyClientOptions(
                    endpoints = listOf("localhost:$serverPort", "localhost:$secondPort"),
                    snapshotDir = null,
                    reconnectMin = 50.milliseconds,
                    reconnectMax = 500.milliseconds,
                    leaseTtl = LEASE_TTL,
                ),
            ).also { clients += it }
            client.namespace(NAMESPACE).instances.register("order", ENDPOINT, instanceName = "order-1")
            val view = client.namespace(NAMESPACE).configs.watch("a")
            first.putConfig(NAMESPACE, "a", "before")
            view.first { it?.config?.content == "before" }
            val lease = instances().single().meta.lease

            first.close()
            server = second
            second.putConfig(NAMESPACE, "a", "after")
            view.first { it?.config?.content == "after" }
            delay(LONGER_THAN_TTL)
            instances().single().meta.lease.assert().isEqualTo(lease)
        } finally {
            server = second
        }
    }

    // --- partitions ---

    @Disabled(
        "Leases cut off together expire over one renewal period, and each is reaped as it expires, so the share of " +
            "expired leases never reaches the threshold. It needs a design decision.",
    )
    @Test
    fun `a partition cutting off most clients for longer than the ttl deletes no live instance`() = scenario {
        startServer(protectionMinLeases = 2).createNamespace(NAMESPACE)
        delay(PAST_GRACE)
        val partitioned = List(CLIENTS) { index ->
            val (client, proxy) = clientBehindProxy()
            client.namespace(NAMESPACE).instances.register("order", ENDPOINT, instanceName = "order-$index")
            proxy
        }
        val leases = instances().associate { it.meta.name to it.meta.lease }
        leases.size.assert().isEqualTo(CLIENTS)
        val seen = CopyOnWriteArrayList<WatchResponse>()
        val observer = observe(seen)
        delay(SETTLE)

        partitioned.forEach { it.disable() }
        delay(LONGER_THAN_TTL)
        partitioned.forEach { it.enable() }
        delay(PAST_GRACE)

        deletions(seen).assert().isEmpty()
        instances().associate { it.meta.name to it.meta.lease }.assert().isEqualTo(leases)
        observer.cancel()
        partitioned.forEach { it.delete() }
    }

    @Test
    fun `a lone client cut off for longer than the ttl leaves, and comes back on healing`() = scenario {
        startServer(protectionMinLeases = 2).createNamespace(NAMESPACE)
        delay(PAST_GRACE)
        val (cutOff, proxy) = clientBehindProxy()
        cutOff.namespace(NAMESPACE).instances.register("order", ENDPOINT, instanceName = "cut-off")
        repeat(CLIENTS) { index ->
            client().namespace(NAMESPACE).instances.register("order", ENDPOINT, instanceName = "steady-$index")
        }
        val lease = instances().first { it.meta.name == "cut-off" }.meta.lease
        val seen = CopyOnWriteArrayList<WatchResponse>()
        val observer = observe(seen)
        delay(SETTLE)

        proxy.disable()
        // A dead process looks just like this, and it must go: protection is only for most of them at once.
        eventually { deletions(seen).contains("cut-off") }
        deletions(seen).assert().containsOnly("cut-off")

        proxy.enable()
        eventually { instances().firstOrNull { it.meta.name == "cut-off" }?.meta?.lease?.let { it != lease } == true }
        observer.cancel()
        proxy.delete()
    }

    // --- timelines ---

    @Test
    fun `a namespace deleted and created again reaches a view as a new timeline`() = scenario {
        startServer().createNamespace(NAMESPACE)
        val view = client().namespace(NAMESPACE).configs.watch("a")
        running.putConfig(NAMESPACE, "a", "old")
        view.first { it?.config?.content == "old" }

        val configKey = RunningServer.config(NAMESPACE, "a", "old").key
        running.resources.delete(DeleteRequest.newBuilder().setKey(configKey).build())
        view.first { it == null }
        running.resources.delete(DeleteRequest.newBuilder().setKey(namespaceKey()).build())

        eventually {
            running.createNamespace(NAMESPACE)
            running.putConfig(NAMESPACE, "a", "new")
            true
        }
        view.first { it?.config?.content == "new" }
        val request = getRequest {
            key = configKey
            minRevision = 1
        }
        eventually { running.resources.get(request).config.content == "new" }
    }

    private fun namespaceKey(): ResourceKey = ResourceKey.newBuilder()
        .setNamespace(NamingRules.SYSTEM_NAMESPACE)
        .setKind(Kind.KIND_NAMESPACE)
        .setName(NAMESPACE)
        .build()

    private companion object {
        const val NAMESPACE = "faults"
        const val TOXIPROXY_FIRST_PORT = 8666
        const val CLIENTS = 3

        /** Beyond any number of leases here, which turns the pause on mass expiry off. */
        const val NO_PROTECTION = 1000
        val LEASE_TTL = 6.seconds
        val LONGER_THAN_TTL = 9.seconds
        val GRACE = 3.seconds
        val WITHIN_GRACE = 2.seconds
        val PAST_GRACE = 5.seconds
        val SETTLE = 300.milliseconds
        val POLL = 100.milliseconds
        val SCENARIO_TIMEOUT = 120.seconds
        val ENDPOINT: Endpoint = Endpoint.newBuilder().setScheme("http").setHost("localhost").setPort(8080).build()
        val NEXT_PROXY = AtomicInteger()
        val NETWORK: Network = Network.newNetwork()
        val TOXIPROXY: ToxiproxyContainer = ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.12.0")
            .withNetwork(NETWORK)
            .withAccessToHost(true)
            .also { it.start() }
        val TOXIPROXY_CLIENT = ToxiproxyClient(TOXIPROXY.host, TOXIPROXY.controlPort)
    }
}
