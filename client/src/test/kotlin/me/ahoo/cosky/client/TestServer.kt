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
import me.ahoo.cosky.server.CoSkyProperties
import me.ahoo.cosky.server.adapter.grpc.LeaseGrpcService
import me.ahoo.cosky.server.adapter.grpc.ResourceGrpcService
import me.ahoo.cosky.server.adapter.grpc.WatchGrpcService
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.memory.MemoryStore
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * The real server code on an in-process transport and a memory store, which a test can stop and start while the store
 * stays. Tests get real protocol behavior rather than a fake's assumptions about it.
 */
internal class TestServer : AutoCloseable {
    val name = "cosky-${UUID.randomUUID()}"
    val store = MemoryStore(keepRevisions = 64)
    val authorizations = CopyOnWriteArrayList<String>()
    private var scope = newScope()
    private var server: Server? = null

    fun start() {
        val caches = WatchCache(store, WINDOW, scope)
        server = InProcessServerBuilder.forName(name)
            .addService(ServerInterceptors.intercept(ResourceGrpcService(store, caches), recording))
            .addService(LeaseGrpcService(store))
            .addService(WatchGrpcService(caches, CoSkyProperties()))
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
            headers.get(AUTHORIZATION)?.let { authorizations += it }
            return next.startCall(call, headers)
        }
    }

    private fun newScope() = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)

    private companion object {
        const val WINDOW = 1000
        val AUTHORIZATION: Metadata.Key<String> = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
    }
}
