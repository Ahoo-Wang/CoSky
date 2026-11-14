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

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.Endpoint
import me.ahoo.cosky.api.v1.ErrorReason
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
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
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
        interceptors: List<ClientInterceptor> = emptyList(),
        serverName: (String) -> String = { server.name },
    ): CoSkyClient = CoSkyClient(
        CoSkyClientOptions(endpoints) {
            this.token = token
            this.clientName = clientName
            this.snapshotDir = snapshotDir
            snapshotIncludeConfig = includeConfig
            snapshotDebounce = 10.milliseconds
            resyncInterval = resync
            reconnectMin = 10.milliseconds
            reconnectMax = 100.milliseconds
            reRegisterJitter = Duration.ZERO
            this.leaseTtl = leaseTtl
            this.callTimeout = callTimeout
            channelFactory = { InProcessChannelBuilder.forName(serverName(it)).intercept(interceptors).build() }
        },
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

    private fun endpoint(host: String = ENDPOINT.host, port: Int = ENDPOINT.port): Endpoint =
        ENDPOINT.toBuilder().setHost(host).setPort(port).build()

    private suspend fun instancesOnServer(): List<Resource> =
        server.store.readAll(NAMESPACE).toList().filter { it.hasInstance() }

    private suspend fun eventually(condition: suspend () -> Boolean) {
        while (!condition()) {
            delay(10.milliseconds)
        }
    }

    /** The first call of [method] fails without a status, like one that starts just as an in-process server stops. */
    private fun failingOnce(method: String): ClientInterceptor = object : FirstCallOf(method) {
        override fun <Req, Resp> replace(call: ClientCall<Req, Resp>): ClientCall<Req, Resp> =
            object : ForwardingClientCall.SimpleForwardingClientCall<Req, Resp>(call) {
                override fun start(responseListener: Listener<Resp>, headers: Metadata) =
                    throw NullPointerException("'executor' must not be null.")
            }
    }

    /** The first call of [method] is never answered; [started] completes once it is made. */
    private fun unansweredOnce(method: String, started: CompletableDeferred<Unit>): ClientInterceptor =
        object : FirstCallOf(method) {
            override fun <Req, Resp> replace(call: ClientCall<Req, Resp>): ClientCall<Req, Resp> =
                object : ClientCall<Req, Resp>() {
                    override fun start(responseListener: Listener<Resp>, headers: Metadata) {
                        started.complete(Unit)
                    }

                    override fun request(numMessages: Int) = Unit

                    override fun cancel(message: String?, cause: Throwable?) = Unit

                    override fun halfClose() = Unit

                    override fun sendMessage(message: Req) = Unit
                }
        }

    /** Hands the first call of one method to [replace], and every other call on as it is. */
    private abstract class FirstCallOf(private val method: String) : ClientInterceptor {
        private val replaced = AtomicBoolean()

        abstract fun <Req, Resp> replace(call: ClientCall<Req, Resp>): ClientCall<Req, Resp>

        override fun <Req, Resp> interceptCall(
            method: MethodDescriptor<Req, Resp>,
            callOptions: CallOptions,
            next: Channel,
        ): ClientCall<Req, Resp> {
            val call = next.newCall(method, callOptions)
            return if (method.bareMethodName == this.method && !replaced.getAndSet(true)) replace(call) else call
        }
    }

    // --- views ---

    @Test
    fun `a watch the server refuses says why, and recovers once that changes`() = scenario {
        val configs = client().namespace("elsewhere").configs
        val refused = runCatching { configs.load(CONFIG) }.exceptionOrNull() as CoSkyException
        refused.reason.assert().isEqualTo(ErrorReason.ERROR_REASON_NAMESPACE_NOT_FOUND)
        server.store.createNamespace("elsewhere")
        while (runCatching { configs.load(CONFIG) }.isFailure) {
            delay(10.milliseconds)
        }
    }

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

    /**
     * A write goes through a temporary file, which may be gone by the time the walk gets to it.
     */
    private fun snapshotFiles(): List<Path> = try {
        Files.walk(snapshots).use { paths ->
            paths.filter { it.isRegularFile() && it.name.endsWith(".pb") }.toList()
        }
    } catch (_: UncheckedIOException) {
        snapshotFiles()
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
        registration.instanceName.assert().isEqualTo("order_10.0.0.1_8080")
        val stored = instancesOnServer().single()
        stored.instance.service.assert().isEqualTo("order")
        stored.meta.lease.assert().isGreaterThan(0)
    }

    @Test
    fun `endpoints whose service and host hold a dash keep apart under their default names`() = scenario {
        val instances = client().namespace(NAMESPACE).instances
        val first = instances.register("billing-api", endpoint(host = "node"))
        val second = instances.register("billing", endpoint(host = "api-node"))
        first.instanceName.assert().isNotEqualTo(second.instanceName)
        instancesOnServer().map { it.instance.service }.assert().containsExactlyInAnyOrder("billing-api", "billing")
    }

    @Test
    fun `endpoints of a long host keep apart under their default names`() = scenario {
        val instances = client().namespace(NAMESPACE).instances
        val service = "s".repeat(63)
        val host = List(3) { "h".repeat(63) }.joinToString(".")
        val first = instances.register(service, endpoint(host = host, port = 8080))
        val second = instances.register(service, endpoint(host = host, port = 8081))
        first.instanceName.assert().isNotEqualTo(second.instanceName)
        instancesOnServer().map { it.instance.endpoint.port }.assert().containsExactlyInAnyOrder(8080, 8081)
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
    fun `closing still finishes when revoking fails without a status`() = scenario {
        val client = client(interceptors = listOf(failingOnce("Revoke")))
        client.namespace(NAMESPACE).instances.register("order", ENDPOINT)
        client.close()
        client.connectionState.value.assert().isEqualTo(ConnectionState.CLOSED)
    }

    @Test
    fun `closing the last registration succeeds though its lease cannot be revoked`() = scenario {
        val instances = client(interceptors = listOf(failingOnce("Revoke"))).namespace(NAMESPACE).instances
        instances.register("order", ENDPOINT, instanceName = "a").close()
        instancesOnServer().assert().isEmpty()
        // The lease is left to expire, and registering goes on as before.
        instances.register("order", ENDPOINT, instanceName = "b")
        instancesOnServer().map { it.meta.name }.assert().containsExactly("b")
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
    fun `a renewal stream that goes silent is opened again, rather than waited on until the lease runs out`() =
        scenario {
            client(leaseTtl = 6.seconds, callTimeout = 1.seconds).namespace(NAMESPACE).instances
                .register("order", ENDPOINT, instanceName = "a")
            eventually { server.renewalStreams.get() == 1 }
            server.answerRenewals = false

            // A period of 2 seconds and a call's time of 1: the client gives up on the stream after 3.
            eventually { server.renewalStreams.get() > 1 }
        }

    @Test
    fun `a renewal that fails without a status is tried again, rather than ending the renewals for good`() =
        scenario {
            val instances = client(leaseTtl = 6.seconds, interceptors = listOf(failingOnce("KeepAlive")))
                .namespace(NAMESPACE).instances
            instances.register("order", ENDPOINT, instanceName = "a")
            val lost = instancesOnServer().single().meta.lease
            server.store.expireLease(NAMESPACE, lost, "test", force = true)

            // Only renewing finds the lease lost and restores the registration.
            eventually { instancesOnServer().isNotEmpty() }
            instancesOnServer().single().meta.lease.assert().isNotEqualTo(lost)
        }

    @Test
    fun `a replacement made while the server is away goes out once one answers`() = scenario {
        val instances = client(leaseTtl = 6.seconds).namespace(NAMESPACE).instances
        instances.register("order", endpoint(port = 8080), instanceName = "a")
        val lease = instancesOnServer().single().meta.lease
        server.stop()
        instances.register("order", endpoint(port = 8081), instanceName = "a")
        server.start()
        // The lease outlives the outage and keeps its one instance, so neither losing it nor a short count says so.
        eventually { instancesOnServer().single().instance.endpoint.port == 8081 }
        instancesOnServer().single().meta.lease.assert().isEqualTo(lease)
    }

    @Test
    fun `a registration made while the server is away goes out once it is back, though it repeats an earlier one`() =
        scenario {
            val instances = client(leaseTtl = 6.seconds).namespace(NAMESPACE).instances
            instances.register("order", ENDPOINT, instanceName = "a", weight = 1)
            val registered = instancesOnServer().single()
            write(registered.toBuilder().apply { instanceBuilder.weight = 0 }.build())
            server.stop()
            // Registering again ends an operator's drain (design §6.2), even with what the server accepted before.
            instances.register("order", ENDPOINT, instanceName = "a", weight = 1)
            server.start()

            eventually { instancesOnServer().singleOrNull()?.instance?.weight == 1 }
            instancesOnServer().single().meta.lease.assert().isEqualTo(registered.meta.lease)
        }

    @Test
    fun `a replacement refused once the server is back gives way to the registration it would have replaced`() =
        scenario {
            val instances = client(leaseTtl = 6.seconds).namespace(NAMESPACE).instances
            instances.register("order", ENDPOINT, instanceName = "a")
            val lost = instancesOnServer().single().meta.lease
            server.stop()
            // No server is there to refuse it yet.
            instances.register("order", endpoint(port = 0), instanceName = "a")
            server.start()
            server.store.expireLease(NAMESPACE, lost, "test", force = true)

            eventually { instancesOnServer().isNotEmpty() }
            val restored = instancesOnServer().single()
            restored.meta.lease.assert().isNotEqualTo(lost)
            restored.instance.endpoint.port.assert().isEqualTo(ENDPOINT.port)
        }

    @Test
    fun `a registration the server refuses is an error, and is not tried again`() = scenario {
        val instances = client(leaseTtl = 6.seconds).namespace(NAMESPACE).instances
        val kept = instances.register("order", ENDPOINT, instanceName = "kept")
        val failure = runCatching { instances.register("Not A Service!", ENDPOINT, instanceName = "bad") }
        (failure.exceptionOrNull() as StatusException).status.code.assert().isEqualTo(Status.Code.INVALID_ARGUMENT)
        // Forgotten, it does not hold on to the lease once the last registration is closed.
        kept.close()
        server.store.leaseStats(NAMESPACE).total.assert().isEqualTo(0)
    }

    @Test
    fun `a registration whose call fails without a status is no error, and goes out once the server answers`() =
        scenario {
            val instances = client(leaseTtl = 6.seconds, interceptors = listOf(failingOnce("Put")))
                .namespace(NAMESPACE).instances
            // Nothing refused it, and the server may have it or not, so the client keeps it as if none had answered.
            val registration = instances.register("order", ENDPOINT, instanceName = "a")
            eventually { instancesOnServer().isNotEmpty() }
            registration.close()
            instancesOnServer().assert().isEmpty()
        }

    @Test
    fun `a registration cancelled on its way is forgotten, as the caller has nothing to close it with`() = scenario {
        val putStarted = CompletableDeferred<Unit>()
        val instances = client(leaseTtl = 6.seconds, interceptors = listOf(unansweredOnce("Put", putStarted)))
            .namespace(NAMESPACE).instances
        val registering = launch { instances.register("order", ENDPOINT, instanceName = "cancelled") }
        putStarted.await()
        registering.cancelAndJoin()
        val kept = instances.register("order", ENDPOINT, instanceName = "kept")
        // Forgotten, it does not hold on to the lease once the last registration is closed.
        kept.close()
        server.store.leaseStats(NAMESPACE).total.assert().isEqualTo(0)
    }

    @Test
    fun `a refused replacement keeps the registration it would have replaced, and restores it with the lease`() =
        scenario {
            val instances = client(leaseTtl = 6.seconds).namespace(NAMESPACE).instances
            instances.register("order", ENDPOINT, instanceName = "a")
            val failure = runCatching { instances.register("order", endpoint(port = 0), instanceName = "a") }
            (failure.exceptionOrNull() as StatusException).status.code.assert().isEqualTo(Status.Code.INVALID_ARGUMENT)
            val lost = instancesOnServer().single()
            lost.instance.endpoint.port.assert().isEqualTo(ENDPOINT.port)
            server.store.expireLease(NAMESPACE, lost.meta.lease, "test", force = true)

            eventually { instancesOnServer().isNotEmpty() }
            val restored = instancesOnServer().single()
            restored.meta.lease.assert().isNotEqualTo(lost.meta.lease)
            restored.instance.endpoint.port.assert().isEqualTo(ENDPOINT.port)
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
    fun `a lease ttl the protocol does not take is refused at once, rather than become the server's default`() {
        listOf(Duration.ZERO, 500.milliseconds, 4.seconds, 5500.milliseconds, 301.seconds).forEach { ttl ->
            runCatching { CoSkyClientOptions(listOf("a:1")) { leaseTtl = ttl } }.exceptionOrNull()
                .assert().isInstanceOf(IllegalArgumentException::class.java)
        }
        listOf(5.seconds, 300.seconds).map { CoSkyClientOptions(listOf("a:1")) { leaseTtl = it }.leaseTtl }
            .assert().containsExactly(5.seconds, 300.seconds)
    }

    @Test
    fun `options say nothing of the token`() {
        CoSkyClientOptions(listOf("a:1")) { token = "cosky_id_secret" }.toString().assert().doesNotContain("secret")
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
    fun `putting back a lost instance leaves the others as an operator changed them`() = scenario {
        val instances = client(leaseTtl = 6.seconds).namespace(NAMESPACE).instances
        instances.register("order", ENDPOINT, instanceName = "a", weight = 1)
        instances.register("order", ENDPOINT, instanceName = "b", weight = 1)
        val registered = instancesOnServer().associateBy { it.meta.name }
        // Drained the way design §6.2 says to take out a running instance: it keeps its lease.
        write(registered.getValue("a").toBuilder().apply { instanceBuilder.weight = 0 }.build())
        server.store.apply(NAMESPACE, listOf(Mutation.Delete(registered.getValue("b").key)), "test")

        eventually { instancesOnServer().size == 2 }
        // Registering waits for the put-back to finish, since both take the same lock.
        instances.register("order", ENDPOINT, instanceName = "c")
        val drained = instancesOnServer().single { it.meta.name == "a" }
        drained.instance.hasWeight().assert().isTrue()
        drained.instance.weight.assert().isEqualTo(0)
        drained.meta.lease.assert().isEqualTo(registered.getValue("a").meta.lease)
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
