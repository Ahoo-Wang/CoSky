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
import io.grpc.ForwardingClientCallListener
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import io.grpc.inprocess.InProcessChannelBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.v1.Endpoint
import me.ahoo.cosky.api.v1.PutRequest
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.key
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Where an Instance and the lease that keeps it alive can part: a registration cancelled after the server took it, and
 * a lease that ran out while others were still on it (design §6.2, §7.5).
 */
class LeaseTransitionsTest {
    private val server = TestServer()
    private val clients = mutableListOf<CoSkyClient>()

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

    private fun instances(
        vararg interceptors: ClientInterceptor,
        callTimeout: Duration = 10.seconds,
    ): CoSkyClient.Namespace.Instances = CoSkyClient(
        CoSkyClientOptions(listOf("only")) {
            snapshotDir = null
            reconnectMin = 10.milliseconds
            reconnectMax = 100.milliseconds
            reRegisterJitter = Duration.ZERO
            leaseTtl = 6.seconds
            this.callTimeout = callTimeout
            channelFactory = { InProcessChannelBuilder.forName(server.name).intercept(*interceptors).build() }
        },
    ).also { clients += it }.namespace(NAMESPACE).instances

    private fun scenario(block: suspend CoroutineScope.() -> Unit): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30.seconds, block)
    }

    private suspend fun instancesOnServer(): List<Resource> =
        server.store.readAll(NAMESPACE).toList().filter { it.hasInstance() }

    private suspend fun eventually(condition: suspend () -> Boolean) {
        while (!condition()) {
            delay(10.milliseconds)
        }
    }

    @Test
    fun `a registration the server took, but cancelled before its answer came, is withdrawn`() = scenario {
        val answered = CompletableDeferred<Unit>()
        val instances = instances(answerLostFor("cancelled", answered))
        instances.register("order", ENDPOINT, instanceName = "companion")
        val registering = launch { instances.register("order", ENDPOINT, instanceName = "cancelled") }
        answered.await()
        registering.cancelAndJoin()

        eventually { instancesOnServer().map { it.meta.name } == listOf("companion") }
        // Not by giving up the lease the companion is on.
        server.store.leaseStats(NAMESPACE).total.assert().isEqualTo(1)
    }

    @Test
    fun `a withdrawal reads as of the revision the store holds, however far behind the view is`() = scenario {
        val answered = CompletableDeferred<Unit>()
        val waited = CompletableDeferred<Unit>()
        val instances = instances(answerLostFor("cancelled", answered), failedGetSignal(waited))
        instances.register("order", ENDPOINT, instanceName = "companion")
        // The view stops following: the cancelled Put lands in the store all the same.
        server.cacheFollows.value = false
        val registering = launch { instances.register("order", ENDPOINT, instanceName = "cancelled") }
        answered.await()
        registering.cancelAndJoin()
        // The store says it is there; the view does not reach that revision in time.
        waited.await()

        server.cacheFollows.value = true

        eventually { instancesOnServer().map { it.meta.name } == listOf("companion") }
    }

    @Test
    fun `a withdrawal goes by the name, not by how many Instances the lease holds`() = scenario {
        val answered = CompletableDeferred<Unit>()
        val getsFail = AtomicBoolean(true)
        val instances = instances(answerLostFor("cancelled", answered), failingWhile("Get", getsFail))
        instances.register("order", ENDPOINT, instanceName = "companion")
        // The first renewal is in.
        delay(SETTLE)
        val registering = launch { instances.register("order", ENDPOINT, instanceName = "cancelled") }
        answered.await()
        registering.cancelAndJoin()
        // The companion goes, as an operator or a lost write can make it: the lease holds one Instance, as the client
        // has one registration, but not the same one.
        val companion = instancesOnServer().single { it.meta.name == "companion" }
        server.store.apply(NAMESPACE, listOf(Mutation.Delete(companion.key)), "operator")
        delay(5.seconds)

        getsFail.set(false)
        delay(7.seconds)

        // The cancelled one withdrawn, and the companion found missing and put back.
        instancesOnServer().map { it.meta.name }.assert().containsExactly("companion")
    }

    @Test
    fun `a withdrawal of what never reached the server ends once the Put could no longer land, deleting nothing`() =
        scenario {
            val sent = CompletableDeferred<Unit>()
            val deletes = AtomicInteger()
            val instances = instances(unsentPutOf("never", sent), counting("Delete", deletes), callTimeout = 2.seconds)
            instances.register("order", ENDPOINT, instanceName = "companion")
            val registering = launch { instances.register("order", ENDPOINT, instanceName = "never") }
            sent.await()
            registering.cancelAndJoin()

            // The store answers that there is none; that is final only past the Put's deadline, after a renewal or two.
            eventually { deletes.get() > 0 }
            delay(5.seconds)
            val settled = deletes.get()
            delay(5.seconds)

            deletes.get().assert().isEqualTo(settled)
            instancesOnServer().map { it.meta.name }.assert().containsExactly("companion")
        }

    @Test
    fun `a name registered again after its registration was cancelled stays`() = scenario {
        val answered = CompletableDeferred<Unit>()
        val instances = instances(answerLostFor("again", answered))
        instances.register("order", ENDPOINT, instanceName = "companion")
        val registering = launch { instances.register("order", ENDPOINT, instanceName = "again") }
        answered.await()
        registering.cancelAndJoin()

        instances.register("order", ENDPOINT, instanceName = "again")
        // Long enough for a withdrawal to have run, whichever came first.
        delay(SETTLE)
        instancesOnServer().map { it.meta.name }.sorted().assert().containsExactly("again", "companion")
    }

    @Test
    fun `registering on a lease that ran out moves every registration onto the new one, before the old is reaped`() =
        scenario {
            val renewalsFail = AtomicBoolean()
            val instances = instances(failingWhile("KeepAlive", renewalsFail))
            instances.register("order", ENDPOINT, instanceName = "first")
            val old = instancesOnServer().single().meta.lease
            renewalsFail.set(true)
            // Past the TTL and the grace before reaping.
            server.clock.advance(10.seconds)
            server.store.expiredLeases(NAMESPACE, 10).assert().contains(old)

            instances.register("order", ENDPOINT, instanceName = "second")

            val leases = instancesOnServer().associate { it.meta.name to it.meta.lease }
            leases.getValue("first").assert().isNotEqualTo(old).isEqualTo(leases.getValue("second"))
            renewalsFail.set(false)
            server.store.expireLease(NAMESPACE, old, "test", force = false).assert().isTrue()
            instancesOnServer().map { it.meta.name }.sorted().assert().containsExactly("first", "second")
        }

    @Test
    fun `a registration that cannot move onto the new lease at once goes with the next renewal`() = scenario {
        val renewalsFail = AtomicBoolean()
        val firstPutFails = AtomicBoolean()
        val instances = instances(failingWhile("KeepAlive", renewalsFail), failingPutOf("first", firstPutFails))
        instances.register("order", ENDPOINT, instanceName = "first")
        val old = instancesOnServer().single().meta.lease
        renewalsFail.set(true)
        server.clock.advance(10.seconds)
        firstPutFails.set(true)

        instances.register("order", ENDPOINT, instanceName = "second")

        instancesOnServer().associate { it.meta.name to it.meta.lease }.getValue("first").assert().isEqualTo(old)
        firstPutFails.set(false)
        renewalsFail.set(false)
        eventually {
            val leases = instancesOnServer().associate { it.meta.name to it.meta.lease }
            leases["first"] == leases["second"]
        }
    }

    @Test
    fun `a withdrawal the server cannot take now is tried again with a renewal`() = scenario {
        val answered = CompletableDeferred<Unit>()
        val getsFail = AtomicBoolean(true)
        val instances = instances(answerLostFor("cancelled", answered), failingWhile("Get", getsFail))
        instances.register("order", ENDPOINT, instanceName = "companion")
        val registering = launch { instances.register("order", ENDPOINT, instanceName = "cancelled") }
        answered.await()
        registering.cancelAndJoin()
        delay(SETTLE)
        instancesOnServer().map { it.meta.name }.sorted().assert().containsExactly("cancelled", "companion")

        getsFail.set(false)

        eventually { instancesOnServer().map { it.meta.name } == listOf("companion") }
    }

    @Test
    fun `a withdrawal leaves an Instance that is no longer on the lease it was sent on`() = scenario {
        val answered = CompletableDeferred<Unit>()
        val getsFail = AtomicBoolean(true)
        val instances = instances(answerLostFor("taken", answered), failingWhile("Get", getsFail))
        instances.register("order", ENDPOINT, instanceName = "companion")
        val registering = launch { instances.register("order", ENDPOINT, instanceName = "taken") }
        answered.await()
        registering.cancelAndJoin()
        // Someone else writes it meanwhile, with no lease: it is theirs now.
        val taken = instancesOnServer().single { it.meta.name == "taken" }
        server.store.apply(
            NAMESPACE,
            listOf(Mutation.Put(taken.toBuilder().apply { metaBuilder.lease = 0 }.build())),
            "operator",
        )

        getsFail.set(false)
        // Two renewals, each of which tries the withdrawal again.
        delay(5.seconds)

        instancesOnServer().map { it.meta.name }.sorted().assert().containsExactly("companion", "taken")
    }

    /** The Put of [name] never leaves the client, and is never answered; [sent] completes once it would have gone. */
    private fun unsentPutOf(name: String, sent: CompletableDeferred<Unit>): ClientInterceptor =
        object : ClientInterceptor {
            override fun <Req, Resp> interceptCall(
                descriptor: MethodDescriptor<Req, Resp>,
                options: CallOptions,
                next: Channel,
            ): ClientCall<Req, Resp> {
                val call = next.newCall(descriptor, options)
                if (descriptor.bareMethodName != "Put") {
                    return call
                }
                return object : ForwardingClientCall.SimpleForwardingClientCall<Req, Resp>(call) {
                    @Volatile
                    private var held = false

                    override fun sendMessage(message: Req) {
                        if ((message as PutRequest).resource.meta.name == name) {
                            held = true
                            sent.complete(Unit)
                            return
                        }
                        super.sendMessage(message)
                    }

                    override fun halfClose() {
                        if (!held) super.halfClose()
                    }
                }
            }
        }

    /** Counts the calls of [method] the client makes. */
    private fun counting(method: String, calls: AtomicInteger): ClientInterceptor = object : ClientInterceptor {
        override fun <Req, Resp> interceptCall(
            descriptor: MethodDescriptor<Req, Resp>,
            options: CallOptions,
            next: Channel,
        ): ClientCall<Req, Resp> {
            if (descriptor.bareMethodName == method) {
                calls.incrementAndGet()
            }
            return next.newCall(descriptor, options)
        }
    }

    /** [failed] completes once a Get fails. */
    private fun failedGetSignal(failed: CompletableDeferred<Unit>): ClientInterceptor = object : ClientInterceptor {
        override fun <Req, Resp> interceptCall(
            descriptor: MethodDescriptor<Req, Resp>,
            options: CallOptions,
            next: Channel,
        ): ClientCall<Req, Resp> {
            val call = next.newCall(descriptor, options)
            if (descriptor.bareMethodName != "Get") {
                return call
            }
            return object : ForwardingClientCall.SimpleForwardingClientCall<Req, Resp>(call) {
                override fun start(responseListener: Listener<Resp>, headers: Metadata) = super.start(
                    object : ForwardingClientCallListener.SimpleForwardingClientCallListener<Resp>(responseListener) {
                        override fun onClose(status: Status, trailers: Metadata) {
                            if (!status.isOk) failed.complete(Unit)
                            super.onClose(status, trailers)
                        }
                    },
                    headers,
                )
            }
        }
    }

    /** Puts of [name] fail with UNAVAILABLE while [failing] is set, every other call going through. */
    private fun failingPutOf(name: String, failing: AtomicBoolean): ClientInterceptor = object : ClientInterceptor {
        override fun <Req, Resp> interceptCall(
            descriptor: MethodDescriptor<Req, Resp>,
            options: CallOptions,
            next: Channel,
        ): ClientCall<Req, Resp> {
            val call = next.newCall(descriptor, options)
            if (descriptor.bareMethodName != "Put") {
                return call
            }
            return object : ForwardingClientCall.SimpleForwardingClientCall<Req, Resp>(call) {
                private var listener: Listener<Resp>? = null

                override fun start(responseListener: Listener<Resp>, headers: Metadata) {
                    listener = responseListener
                    super.start(responseListener, headers)
                }

                override fun sendMessage(message: Req) {
                    if (failing.get() && (message as PutRequest).resource.meta.name == name) {
                        cancel("Out of reach on purpose.", null)
                        listener?.onClose(Status.UNAVAILABLE.withDescription("Out of reach on purpose."), Metadata())
                        return
                    }
                    super.sendMessage(message)
                }
            }
        }
    }

    /** Calls of [method] fail with UNAVAILABLE while [failing] is set, as when the server is out of reach. */
    private fun failingWhile(method: String, failing: AtomicBoolean): ClientInterceptor = object : ClientInterceptor {
        override fun <Req, Resp> interceptCall(
            descriptor: MethodDescriptor<Req, Resp>,
            options: CallOptions,
            next: Channel,
        ): ClientCall<Req, Resp> {
            val call = next.newCall(descriptor, options)
            if (descriptor.bareMethodName != method || !failing.get()) {
                return call
            }
            return object : ForwardingClientCall.SimpleForwardingClientCall<Req, Resp>(call) {
                override fun start(responseListener: Listener<Resp>, headers: Metadata) =
                    responseListener.onClose(Status.UNAVAILABLE.withDescription("Out of reach on purpose."), Metadata())

                override fun request(numMessages: Int) = Unit

                override fun sendMessage(message: Req) = Unit

                override fun halfClose() = Unit
            }
        }
    }

    /**
     * The server takes the first Put of [name], but its answer never reaches the client. [answered] completes once the
     * server has answered.
     */
    private fun answerLostFor(name: String, answered: CompletableDeferred<Unit>): ClientInterceptor =
        object : ClientInterceptor {
            override fun <Req, Resp> interceptCall(
                descriptor: MethodDescriptor<Req, Resp>,
                options: CallOptions,
                next: Channel,
            ): ClientCall<Req, Resp> {
                val call = next.newCall(descriptor, options)
                if (descriptor.bareMethodName != "Put") {
                    return call
                }
                return object : ForwardingClientCall.SimpleForwardingClientCall<Req, Resp>(call) {
                    @Volatile
                    private var losing = false

                    override fun start(responseListener: Listener<Resp>, headers: Metadata) = super.start(
                        object : ForwardingClientCallListener.SimpleForwardingClientCallListener<Resp>(
                            responseListener
                        ) {
                            override fun onMessage(message: Resp) {
                                if (losing) answered.complete(Unit) else super.onMessage(message)
                            }

                            override fun onClose(status: Status, trailers: Metadata) {
                                if (!losing) super.onClose(status, trailers)
                            }
                        },
                        headers,
                    )

                    override fun sendMessage(message: Req) {
                        losing = (message as PutRequest).resource.meta.name == name && !answered.isCompleted
                        super.sendMessage(message)
                    }
                }
            }
        }

    private companion object {
        const val NAMESPACE = "prod"
        val ENDPOINT: Endpoint = Endpoint.newBuilder().setScheme("http").setHost("10.0.0.1").setPort(8080).build()
        val SETTLE = 300.milliseconds
    }
}
