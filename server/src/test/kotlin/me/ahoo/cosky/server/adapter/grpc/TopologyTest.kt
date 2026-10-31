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

package me.ahoo.cosky.server.adapter.grpc

import io.grpc.Metadata
import io.grpc.ServerInterceptors
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.MetadataUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.TopologyEdge
import me.ahoo.cosky.api.v1.WatchRequest
import me.ahoo.cosky.api.v1.WatchServiceGrpcKt
import me.ahoo.cosky.server.CoSkyProperties
import me.ahoo.cosky.server.view.TopologyRecorder
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TopologyTest {
    private val store = MemoryStore()
    private val recorder = TopologyRecorder(store, 1.minutes)

    private fun scenario(block: suspend (WatchServiceGrpcKt.WatchServiceCoroutineStub, String) -> Unit): Unit =
        runBlocking(VirtualThreads.dispatcher) {
            val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)
            val name = UUID.randomUUID().toString()
            val service = WatchGrpcService(WatchCache(store, 64, scope), CoSkyProperties(), topology = recorder)
            val server = InProcessServerBuilder.forName(name)
                .addService(ServerInterceptors.intercept(service, ClientNameInterceptor()))
                .build()
                .start()
            val channel = InProcessChannelBuilder.forName(name).build()
            try {
                withTimeout(10.seconds) {
                    store.createNamespace(NAMESPACE)
                    block(WatchServiceGrpcKt.WatchServiceCoroutineStub(channel), name)
                }
            } finally {
                channel.shutdownNow()
                server.shutdownNow()
                scope.cancel()
            }
        }

    private fun named(name: String): Metadata = Metadata().apply {
        put(Metadata.Key.of("cosky-client-name", Metadata.ASCII_STRING_MARSHALLER), name)
    }

    private fun watch(kind: Kind, service: String = "") = flow {
        emit(
            WatchRequest.newBuilder().apply {
                createBuilder.setWatchId(1).setNamespace(NAMESPACE).setKind(kind).setService(service)
            }.build(),
        )
        CompletableDeferred<Unit>().await()
    }

    @Test
    fun `a client watching one service consumes it`() = scenario { stub, _ ->
        val named = stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(named("order")))
        named.watch(watch(Kind.KIND_INSTANCE, "payment")).first {
            recorder.flush()
            true
        }
        store.topology(NAMESPACE).assert()
            .containsExactly(TopologyEdge.newBuilder().setConsumer("order").setProducer("payment").build())
    }

    @Test
    fun `an edge is no longer refreshed once its watch ends`() = scenario { stub, _ ->
        val named = stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(named("order")))
        named.watch(watch(Kind.KIND_INSTANCE, "payment")).first()
        // The server notices the cancellation a moment later.
        withTimeout(5.seconds) {
            while (recorder.hasEdges(NAMESPACE)) {
                delay(10.milliseconds)
            }
        }
    }

    @Test
    fun `watching a whole kind says nothing about what a client depends on`() = scenario { stub, _ ->
        val named = stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(named("order")))
        named.watch(watch(Kind.KIND_INSTANCE)).first {
            recorder.flush()
            true
        }
        store.topology(NAMESPACE).assert().isEmpty()
    }

    @Test
    fun `a client with no name and no subject is not recorded`() = scenario { stub, _ ->
        stub.watch(watch(Kind.KIND_INSTANCE, "payment")).first {
            recorder.flush()
            true
        }
        store.topology(NAMESPACE).assert().isEmpty()
    }

    @Test
    fun `a malformed name is ignored`() = scenario { stub, _ ->
        val named = stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(named("not a name!")))
        named.watch(watch(Kind.KIND_INSTANCE, "payment")).first {
            recorder.flush()
            true
        }
        store.topology(NAMESPACE).assert().isEmpty()
    }

    private companion object {
        const val NAMESPACE = "topology"
    }
}
