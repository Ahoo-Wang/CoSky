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

package me.ahoo.cosky.client

import io.grpc.Status
import io.grpc.StatusException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.Endpoint
import me.ahoo.cosky.api.v1.GrantRequest
import me.ahoo.cosky.api.v1.GrantResponse
import me.ahoo.cosky.api.v1.LeaseServiceGrpcKt
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.WatchRequest
import me.ahoo.cosky.api.v1.WatchResponse
import me.ahoo.cosky.api.v1.WatchServiceGrpcKt
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.instanceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.key
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CoSkyClientTest {
    private val server = TestServer()
    private val clients = mutableListOf<CoSkyClient>()

    @TempDir
    lateinit var snapshots: Path

    @BeforeEach
    fun start() {
        server.start()
        runBlocking { server.store.createNamespace(NAMESPACE) }
    }

    @AfterEach
    fun stop() {
        clients.forEach { it.close() }
        server.close()
    }

    private fun client(
        snapshotDir: Path? = null,
        includeConfig: Boolean = true,
        token: String? = null,
        clientName: String? = null,
        leaseTtl: Duration = 30.seconds,
        resync: Duration = 30.minutes,
        endpoints: List<String> = listOf("only"),
        callTimeout: Duration = 10.seconds,
        serverName: (String) -> String = { server.name },
    ): CoSkyClient = CoSkyClient(
        CoSkyClientOptions(
            endpoints = endpoints,
            token = token,
            clientName = clientName,
            snapshotDir = snapshotDir,
            snapshotIncludeConfig = includeConfig,
            snapshotDebounce = 10.milliseconds,
            resyncInterval = resync,
            reconnectMin = 10.milliseconds,
            reconnectMax = 100.milliseconds,
            reRegisterJitter = Duration.ZERO,
            leaseTtl = leaseTtl,
            callTimeout = callTimeout,
            channelFactory = { InProcessChannelBuilder.forName(serverName(it)).build() },
        ),
    ).also { clients += it }

    private fun scenario(block: suspend CoroutineScope.() -> Unit): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30.seconds, block)
    }

    private fun config(name: String = CONFIG, content: String = "a: 1"): Resource = resource {
        meta = objectMeta {
            namespace = NAMESPACE
            this.name = name
        }
        config = configSpec {
            format = ConfigFormat.CONFIG_FORMAT_YAML
            this.content = content
        }
    }

    private fun instance(name: String, service: String = "order"): Resource = resource {
        meta = objectMeta {
            namespace = NAMESPACE
            this.name = name
        }
        instance = instanceSpec {
            this.service = service
            endpoint = ENDPOINT
        }
    }

    private suspend fun write(resource: Resource) {
        server.store.apply(NAMESPACE, listOf(Mutation.Put(resource)), "test")
    }

    private suspend fun instancesOnServer(): List<Resource> =
        server.store.readAll(NAMESPACE).toList().filter { it.hasInstance() }

    private suspend fun eventually(condition: suspend () -> Boolean) {
        while (!condition()) {
            delay(10.milliseconds)
        }
    }

    // --- views ---

    @Test
    fun `a config view follows writes and deletes`() = scenario {
        val view = client().namespace(NAMESPACE).configs.watch(CONFIG)
        write(config(content = "first"))
        view.first { it?.config?.content == "first" }
        write(config(content = "second"))
        view.first { it?.config?.content == "second" }
        server.store.apply(NAMESPACE, listOf(Mutation.Delete(config().key)), "test")
        view.first { it == null }
    }

    @Test
    fun `an instance view holds only its service, in name order`() = scenario {
        val view = client().namespace(NAMESPACE).instances.watch("order")
        write(instance("order-2"))
        write(instance("order-1"))
        write(instance("payment-1", service = "payment"))
        val names = view.first { it.size == 2 }.map { it.meta.name }
        names.assert().containsExactly("order-1", "order-2")
    }

    @Test
    fun `the same key shares one view`() {
        val namespace = client().namespace(NAMESPACE)
        namespace.configs.watch(CONFIG).assert().isSameAs(namespace.configs.watch(CONFIG))
        namespace.instances.watch("order").assert().isSameAs(namespace.instances.watch("order"))
    }

    @Test
    fun `a view starts from what the server already has`() = scenario {
        write(config())
        client().namespace(NAMESPACE).configs.watch(CONFIG).first { it != null }
    }

    // --- connection ---

    @Test
    fun `a client that follows is synced, and one that is closed says so`() = scenario {
        val client = client()
        client.connectionState.value.assert().isEqualTo(ConnectionState.CONNECTING)
        write(config())
        client.namespace(NAMESPACE).configs.watch(CONFIG).first { it != null }
        client.connectionState.first { it == ConnectionState.SYNCED }
        client.close()
        clients.remove(client)
        client.connectionState.value.assert().isEqualTo(ConnectionState.CLOSED)
    }

    @Test
    fun `a view survives a server outage and resumes without missing anything`() = scenario {
        val client = client()
        val view = client.namespace(NAMESPACE).configs.watch(CONFIG)
        write(config(content = "before"))
        view.first { it?.config?.content == "before" }

        server.stop()
        client.connectionState.first { it == ConnectionState.DEGRADED }
        view.value?.config?.content.assert().isEqualTo("before")

        write(config(content = "during"))
        server.start()
        view.first { it?.config?.content == "during" }
        client.connectionState.first { it == ConnectionState.SYNCED }
    }

    @Test
    fun `lost writes reach the view through a new timeline`() = scenario {
        val view = client().namespace(NAMESPACE).configs.watch(CONFIG)
        write(config(content = "kept"))
        write(config(content = "lost"))
        view.first { it?.config?.content == "lost" }
        server.store.loseWritesAfter(NAMESPACE, 1)
        view.first { it?.config?.content == "kept" }
    }

    @Test
    fun `the next endpoint is tried when one fails`() = scenario {
        val client = client(endpoints = listOf("dead", "alive")) { if (it == "alive") server.name else "nobody" }
        write(config())
        client.namespace(NAMESPACE).configs.watch(CONFIG).first { it != null }
    }

    @Test
    fun `the token is sent as a bearer`() = scenario {
        client(token = "cosky_id_secret").namespace(NAMESPACE).instances.register("order", ENDPOINT)
        server.authorizations.first().assert().isEqualTo("Bearer cosky_id_secret")
    }

    @Test
    fun `a resync starts the watch over and keeps following`() = scenario {
        val view = client(resync = 50.milliseconds).namespace(NAMESPACE).configs.watch(CONFIG)
        write(config(content = "first"))
        view.first { it?.config?.content == "first" }
        delay(200.milliseconds)
        write(config(content = "second"))
        view.first { it?.config?.content == "second" }
    }

    // --- snapshots ---

    private fun snapshotFiles(): List<Path> = Files.walk(snapshots).use { paths ->
        paths.filter { it.isRegularFile() }.toList()
    }

    @Test
    fun `a new client serves from a snapshot before reaching a server`() = scenario {
        val first = client(snapshots)
        val view = first.namespace(NAMESPACE).configs.watch(CONFIG)
        write(config(content = "saved"))
        view.first { it?.config?.content == "saved" }
        eventually { snapshotFiles().isNotEmpty() }
        delay(50.milliseconds)
        first.close()
        clients.remove(first)
        server.stop()

        val second = client(snapshots)
        second.namespace(NAMESPACE).configs.watch(CONFIG).value?.config?.content.assert().isEqualTo("saved")
        second.connectionState.value.assert().isEqualTo(ConnectionState.DEGRADED)

        write(config(content = "newer"))
        server.start()
        second.namespace(NAMESPACE).configs.watch(CONFIG).first { it?.config?.content == "newer" }
    }

    @Test
    fun `snapshots are private to their owner`() = scenario {
        val view = client(snapshots).namespace(NAMESPACE).configs.watch(CONFIG)
        write(config())
        view.first { it != null }
        eventually { snapshotFiles().any { it.name.endsWith(".pb") } }
        val file = snapshotFiles().first { it.name.endsWith(".pb") }
        PosixFilePermissions.toString(Files.getPosixFilePermissions(file)).assert().isEqualTo("rw-------")
        PosixFilePermissions.toString(Files.getPosixFilePermissions(file.parent)).assert().isEqualTo("rwx------")
    }

    @Test
    fun `configs can stay off the disk`() = scenario {
        val namespace = client(snapshots, includeConfig = false).namespace(NAMESPACE)
        val configs = namespace.configs.watch(CONFIG)
        val instances = namespace.instances.watch("order")
        write(config())
        write(instance("order-1"))
        configs.first { it != null }
        instances.first { it.isNotEmpty() }
        eventually { snapshotFiles().isNotEmpty() }
        delay(50.milliseconds)
        snapshotFiles().map { it.name }.assert().allMatch { it.startsWith("instance-") }
    }

    @Test
    fun `another identity does not read a snapshot`() = scenario {
        val first = client(snapshots, token = "cosky_one_secret")
        val view = first.namespace(NAMESPACE).configs.watch(CONFIG)
        write(config())
        view.first { it != null }
        eventually { snapshotFiles().isNotEmpty() }
        server.stop()
        client(snapshots, token = "cosky_two_secret").namespace(NAMESPACE).configs.watch(CONFIG).value.assert().isNull()
        server.start()
    }

    // --- registration ---

    @Test
    fun `a registration appears on the server with a default name`() = scenario {
        val registration = client().namespace(NAMESPACE).instances.register("order", ENDPOINT)
        registration.instanceName.assert().isEqualTo("order-10.0.0.1-8080")
        val stored = instancesOnServer().single()
        stored.instance.service.assert().isEqualTo("order")
        stored.meta.lease.assert().isGreaterThan(0)
    }

    @Test
    fun `registrations share one lease, and closing the last revokes it`() = scenario {
        val instances = client().namespace(NAMESPACE).instances
        val first = instances.register("order", ENDPOINT, instanceName = "a")
        val second = instances.register("order", ENDPOINT, instanceName = "b")
        instancesOnServer().map { it.meta.lease }.toSet().size.assert().isEqualTo(1)
        first.close()
        instancesOnServer().map { it.meta.name }.assert().containsExactly("b")
        server.store.leaseStats(NAMESPACE).total.assert().isEqualTo(1)
        second.close()
        instancesOnServer().assert().isEmpty()
        server.store.leaseStats(NAMESPACE).total.assert().isEqualTo(0)
    }

    @Test
    fun `closing the client revokes its lease`() = scenario {
        val client = client()
        client.namespace(NAMESPACE).instances.register("order", ENDPOINT)
        client.close()
        clients.remove(client)
        instancesOnServer().assert().isEmpty()
    }

    @Test
    fun `a lost lease is restored with every registration`() = scenario {
        val instances = client(leaseTtl = 6.seconds).namespace(NAMESPACE).instances
        instances.register("order", ENDPOINT, instanceName = "a", metadata = mapOf("zone" to "az1"), weight = 3)
        instances.register("order", ENDPOINT, instanceName = "b")
        val lost = instancesOnServer().first().meta.lease
        server.store.expireLease(NAMESPACE, lost, "test", force = true)
        instancesOnServer().assert().isEmpty()

        eventually { instancesOnServer().size == 2 }
        val restored = instancesOnServer()
        restored.map { it.meta.lease }.toSet().single().assert().isNotEqualTo(lost)
        restored.first { it.meta.name == "a" }.instance.metadataMap.assert().containsEntry("zone", "az1")
        restored.first { it.meta.name == "a" }.instance.weight.assert().isEqualTo(3)
    }

    @Test
    fun `closing still finishes when the server is out of reach`() = scenario {
        val client = client()
        client.namespace(NAMESPACE).instances.register("order", ENDPOINT)
        server.stop()
        client.close()
        client.connectionState.value.assert().isEqualTo(ConnectionState.CLOSED)
    }

    @Test
    fun `a registration that cannot be closed stays, and keeps being renewed`() = scenario {
        val client = client(leaseTtl = 6.seconds)
        val registration = client.namespace(NAMESPACE).instances.register("order", ENDPOINT, instanceName = "order-1")
        server.stop()
        (runCatching { registration.close() }.exceptionOrNull() is StatusException).assert().isTrue()
        server.start()
        // It is still registered, so closing can be tried again, and it now succeeds.
        eventually {
            runCatching { registration.close() }.isSuccess
        }
        instancesOnServer().assert().isEmpty()
    }

    @Test
    fun `a registration made while the server is away goes out once one answers`() = scenario {
        server.stop()
        val registration = client(leaseTtl = 6.seconds).namespace(NAMESPACE).instances
            .register("order", ENDPOINT, instanceName = "order-1")
        server.start()
        eventually { instancesOnServer().isNotEmpty() }
        instancesOnServer().single().meta.lease.assert().isGreaterThan(0)
        registration.close()
        instancesOnServer().assert().isEmpty()
    }

    @Test
    fun `a registration closed before the server came back leaves nothing behind`() = scenario {
        server.stop()
        val registration = client(leaseTtl = 6.seconds).namespace(NAMESPACE).instances
            .register("order", ENDPOINT, instanceName = "order-1")
        server.start()
        eventually { runCatching { registration.close() }.isSuccess }
        delay(500.milliseconds)
        instancesOnServer().assert().isEmpty()
    }

    @Test
    fun `a registration the server refuses is an error, and is not tried again`() = scenario {
        val instances = client(leaseTtl = 6.seconds).namespace(NAMESPACE).instances
        val failure = runCatching { instances.register("Not A Service!", ENDPOINT, instanceName = "bad") }
        (failure.exceptionOrNull() as StatusException).status.code.assert().isEqualTo(Status.Code.INVALID_ARGUMENT)
    }

    @Test
    fun `a server that never answers does not hold up a registration`() = scenario {
        val name = UUID.randomUUID().toString()
        val silent = InProcessServerBuilder.forName(name)
            .addService(
                object : LeaseServiceGrpcKt.LeaseServiceCoroutineImplBase() {
                    override suspend fun grant(request: GrantRequest): GrantResponse = awaitCancellation()
                },
            )
            .build()
            .start()
        try {
            client(callTimeout = 100.milliseconds, serverName = { name }).namespace(NAMESPACE).instances
                .register("order", ENDPOINT)
        } finally {
            silent.shutdownNow()
        }
    }

    @Test
    fun `a snapshot cut short by a broken stream leaves nothing of it behind`() = scenario {
        val name = UUID.randomUUID().toString()
        val streams = AtomicInteger()
        val fake = InProcessServerBuilder.forName(name)
            .addService(
                object : WatchServiceGrpcKt.WatchServiceCoroutineImplBase() {
                    override fun watch(requests: Flow<WatchRequest>): Flow<WatchResponse> = flow {
                        val watch = requests.first().create.watchId
                        val broken = streams.incrementAndGet() == 1
                        emit(snapshotOf(watch, if (broken) "deleted-since" else "kept", more = broken))
                        if (broken) {
                            throw StatusException(Status.UNAVAILABLE)
                        }
                        awaitCancellation()
                    }
                },
            )
            .build()
            .start()
        try {
            val instances = client(serverName = { name }).namespace(NAMESPACE).instances.load("order")
            instances.map { it.meta.name }.assert().containsExactly("kept")
        } finally {
            fake.shutdownNow()
        }
    }

    private fun snapshotOf(watch: Long, instanceName: String, more: Boolean): WatchResponse =
        WatchResponse.newBuilder().setWatchId(watch).setSnapshot(
            WatchResponse.Snapshot.newBuilder()
                .addItems(instance(instanceName))
                .setRevision(1)
                .setEpoch("e")
                .setMore(more),
        ).build()

    @Test
    fun `options say nothing of the token`() {
        CoSkyClientOptions(listOf("a:1"), token = "cosky_id_secret").toString().assert().doesNotContain("secret")
    }

    @Test
    fun `a client names itself on every call`() = scenario {
        client(clientName = "order").namespace(NAMESPACE).instances.register("order", ENDPOINT)
        server.clientNames.first().assert().isEqualTo("order")
    }

    @Test
    fun `an instance lost while its lease survives is put back`() = scenario {
        val instances = client(leaseTtl = 6.seconds).namespace(NAMESPACE).instances
        instances.register("order", ENDPOINT)
        val lost = instancesOnServer().single()
        val lease = lost.meta.lease
        // What a failover leaves behind: the lease is there, and the newer write is not.
        server.store.apply(NAMESPACE, listOf(Mutation.Delete(lost.key)), "test")
        eventually { instancesOnServer().isNotEmpty() }
        instancesOnServer().single().meta.lease.assert().isEqualTo(lease)
    }

    @Test
    fun `a failing stream does not count as losing the lease`() = scenario {
        client(leaseTtl = 6.seconds).namespace(NAMESPACE).instances.register("order", ENDPOINT)
        val before = instancesOnServer().single()
        server.stop()
        delay(300.milliseconds)
        server.start()
        delay(2500.milliseconds)
        val after = instancesOnServer().single()
        after.meta.lease.assert().isEqualTo(before.meta.lease)
        after.meta.revision.assert().isEqualTo(before.meta.revision)
    }

    @Test
    fun `a load balancer picks from the local view`() = scenario {
        val namespace = client().namespace(NAMESPACE)
        val balancer = namespace.loadBalancer("order")
        balancer.choose().assert().isNull()
        write(instance("order-1"))
        namespace.instances.watch("order").first { it.isNotEmpty() }
        balancer.choose()?.meta?.name.assert().isEqualTo("order-1")
    }

    private companion object {
        const val NAMESPACE = "prod"
        const val CONFIG = "application.yaml"
        val ENDPOINT: Endpoint = Endpoint.newBuilder().setScheme("http").setHost("10.0.0.1").setPort(8080).build()
    }
}
