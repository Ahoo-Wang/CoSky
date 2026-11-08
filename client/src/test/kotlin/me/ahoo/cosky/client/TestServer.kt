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

import io.grpc.ForwardingServerCall.SimpleForwardingServerCall
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener
import io.grpc.Metadata
import io.grpc.Server
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.ServerInterceptors
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.v1.WatchRequest
import me.ahoo.cosky.server.CoSkyProperties
import me.ahoo.cosky.server.adapter.grpc.LeaseGrpcService
import me.ahoo.cosky.server.adapter.grpc.ResourceGrpcService
import me.ahoo.cosky.server.adapter.grpc.WatchGrpcService
import me.ahoo.cosky.server.lease.LeaseService
import me.ahoo.cosky.server.resource.ResourceService
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.server.watch.WatchService
import me.ahoo.cosky.store.memory.MemoryStore
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The real server code on an in-process transport and a memory store, which a test can stop and start while the store
 * stays. Tests get real protocol behavior rather than a fake's assumptions about it.
 */
internal class TestServer(private val properties: CoSkyProperties = CoSkyProperties()) : AutoCloseable {
    val name = "cosky-${UUID.randomUUID()}"
    val store = MemoryStore(keepRevisions = 64)
    val authorizations = CopyOnWriteArrayList<String>()
    val clientNames = CopyOnWriteArrayList<String>()

    /**
     * The Creates of every watch stream, in the order they arrived.
     */
    val watchStreams = CopyOnWriteArrayList<List<WatchRequest.Create>>()

    /** How many renewal streams were opened. */
    val renewalStreams = AtomicInteger()

    /** Off, renewals still renew the lease, but their answers go nowhere, as on a stream a proxy has dropped. */
    @Volatile
    var answerRenewals = true
    private var scope = newScope()
    private var server: Server? = null

    fun start() {
        val caches = WatchCache(store, WINDOW, scope)
        server = InProcessServerBuilder.forName(name)
            .addService(ServerInterceptors.intercept(ResourceGrpcService(ResourceService(store, caches)), recording))
            .addService(ServerInterceptors.intercept(LeaseGrpcService(LeaseService(store)), silencing))
            .addService(
                ServerInterceptors.intercept(WatchGrpcService(WatchService(caches, properties)), recordingCreates),
            )
            .build()
            .start()
    }

    fun stop() {
        server?.shutdownNow()?.awaitTermination(1, TimeUnit.SECONDS)
        scope.cancel()
        scope = newScope()
    }

    override fun close() = stop()

    private val recording = object : ServerInterceptor {
        override fun <Req, Resp> interceptCall(
            call: ServerCall<Req, Resp>,
            headers: Metadata,
            next: ServerCallHandler<Req, Resp>,
        ): ServerCall.Listener<Req> {
            headers.get(CLIENT_NAME)?.let { clientNames += it }
            headers.get(AUTHORIZATION)?.let { authorizations += it }
            return next.startCall(call, headers)
        }
    }

    private val recordingCreates = object : ServerInterceptor {
        override fun <Req, Resp> interceptCall(
            call: ServerCall<Req, Resp>,
            headers: Metadata,
            next: ServerCallHandler<Req, Resp>,
        ): ServerCall.Listener<Req> {
            val creates = CopyOnWriteArrayList<WatchRequest.Create>().also { watchStreams += it }
            return object : SimpleForwardingServerCallListener<Req>(next.startCall(call, headers)) {
                override fun onMessage(message: Req) {
                    (message as WatchRequest).takeIf { it.hasCreate() }?.let { creates += it.create }
                    super.onMessage(message)
                }
            }
        }
    }

    private val silencing = object : ServerInterceptor {
        override fun <Req, Resp> interceptCall(
            call: ServerCall<Req, Resp>,
            headers: Metadata,
            next: ServerCallHandler<Req, Resp>,
        ): ServerCall.Listener<Req> {
            if (call.methodDescriptor.bareMethodName != "KeepAlive") {
                return next.startCall(call, headers)
            }
            renewalStreams.incrementAndGet()
            val silenced = object : SimpleForwardingServerCall<Req, Resp>(call) {
                override fun sendMessage(message: Resp) {
                    if (answerRenewals) {
                        super.sendMessage(message)
                    }
                }
            }
            return next.startCall(silenced, headers)
        }
    }

    private fun newScope() = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)

    private companion object {
        const val WINDOW = 1000
        val AUTHORIZATION: Metadata.Key<String> = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
        val CLIENT_NAME: Metadata.Key<String> = Metadata.Key.of("cosky-client-name", Metadata.ASCII_STRING_MARSHALLER)
    }
}
