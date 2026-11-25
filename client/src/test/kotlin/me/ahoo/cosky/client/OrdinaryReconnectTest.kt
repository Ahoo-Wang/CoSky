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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall
import io.grpc.ForwardingClientCallListener
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.inprocess.InProcessChannelBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.v1.endpoint
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A server ends the streams of a connection that reached its maximum age, and every stream when it shuts down in a
 * rolling update, with `UNAVAILABLE`. A stream that had been working and ends that way is an ordinary reconnect, which
 * a fleet does every half hour: logged at INFO, not as a failure.
 */
class OrdinaryReconnectTest {
    private val server = TestServer().apply { start() }
    private val logs = ListAppender<ILoggingEvent>().apply { start() }
    private val loggers = listOf(WatchMultiplexer::class.java, LeaseKeeper::class.java)
        .map { LoggerFactory.getLogger(it) as Logger }
        .onEach { it.addAppender(logs) }
    private val clients = mutableListOf<CoSkyClient>()

    /** Renewals the server answered, as the client received them. */
    private val renewals = AtomicInteger()

    @AfterEach
    fun stop() {
        clients.forEach { it.close() }
        loggers.forEach { it.detachAppender(logs) }
        server.close()
    }

    /**
     * Waits long enough before reconnecting for the server to be back, so that the stream that ended is the only one
     * to fail.
     */
    private fun client(): CoSkyClient = CoSkyClient(
        CoSkyClientOptions(listOf("cosky")) {
            snapshotDir = null
            reconnectMin = 1.seconds
            reconnectMax = 2.seconds
            channelFactory = { InProcessChannelBuilder.forName(server.name).intercept(countingRenewals).build() }
        },
    ).also { clients += it }

    private val countingRenewals = object : ClientInterceptor {
        override fun <Req, Resp> interceptCall(
            method: MethodDescriptor<Req, Resp>,
            callOptions: CallOptions,
            next: Channel,
        ): ClientCall<Req, Resp> {
            val call = next.newCall(method, callOptions)
            if (method.bareMethodName != "KeepAlive") {
                return call
            }
            return object : ForwardingClientCall.SimpleForwardingClientCall<Req, Resp>(call) {
                override fun start(responseListener: Listener<Resp>, headers: Metadata) {
                    val counting = object : ForwardingClientCallListener.SimpleForwardingClientCallListener<Resp>(
                        responseListener,
                    ) {
                        override fun onMessage(message: Resp) {
                            renewals.incrementAndGet()
                            super.onMessage(message)
                        }
                    }
                    super.start(counting, headers)
                }
            }
        }
    }

    private fun scenario(block: suspend CoroutineScope.() -> Unit): Unit = runBlocking(Dispatchers.Default) {
        server.store.createNamespace(NAMESPACE)
        withTimeout(30.seconds, block)
    }

    private suspend fun eventually(condition: () -> Boolean) {
        while (!condition()) {
            delay(10.milliseconds)
        }
    }

    /** What [type] logged, as of now. */
    private fun logged(type: Class<*>): List<ILoggingEvent> =
        synchronized(logs) { logs.list.toList() }.filter { it.loggerName == type.name }

    /** Ends every stream with `UNAVAILABLE`, as a server shutting down does, and is back at once. */
    private fun restartServer() {
        server.stop()
        server.start()
    }

    @Test
    fun `a watch stream that ends with UNAVAILABLE once synced is reconnected at INFO`() = scenario {
        val client = client()
        client.namespace(NAMESPACE).configs.watch(CONFIG)
        client.connectionState.first { it == ConnectionState.SYNCED }

        restartServer()
        eventually { logged(WatchMultiplexer::class.java).isNotEmpty() }
        client.connectionState.first { it == ConnectionState.SYNCED }

        val events = logged(WatchMultiplexer::class.java)
        events.map { it.level }.assert().containsOnly(Level.INFO)
        events.first().formattedMessage.assert().contains("ended").contains("UNAVAILABLE").contains("reconnecting")
    }

    @Test
    fun `a renewal stream that ends with UNAVAILABLE after renewing is reconnected at INFO`() = scenario {
        val client = client()
        client.namespace(NAMESPACE).instances.register(service = "order", endpoint = ENDPOINT)
        eventually { renewals.get() > 0 }

        val before = renewals.get()
        restartServer()
        eventually { logged(LeaseKeeper::class.java).isNotEmpty() }
        eventually { renewals.get() > before }

        val events = logged(LeaseKeeper::class.java)
        events.map { it.level }.assert().containsOnly(Level.INFO)
        events.first().formattedMessage.assert().contains("ended").contains("UNAVAILABLE").contains("reconnecting")
    }

    private companion object {
        const val NAMESPACE = "prod"
        const val CONFIG = "application.yaml"
        val ENDPOINT = endpoint {
            scheme = "http"
            host = "10.0.0.1"
            port = 8080
        }
    }
}
