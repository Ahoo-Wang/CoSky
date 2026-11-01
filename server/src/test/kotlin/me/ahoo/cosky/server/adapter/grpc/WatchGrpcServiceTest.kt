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

import io.grpc.Status
import io.grpc.StatusException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.WatchRequest
import me.ahoo.cosky.api.v1.WatchResponse
import me.ahoo.cosky.api.v1.WatchServiceGrpcKt
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.server.CoSkyProperties
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.ChangeBatch
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.Position
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class WatchGrpcServiceTest {
    /**
     * Its changes never reach a cache, as on a replica that stays behind.
     */
    private class StalledStore(private val delegate: Store) : Store by delegate {
        override fun changes(namespace: String, from: Position): Flow<ChangeBatch> = flow {
            CompletableDeferred<Unit>().await()
            emitAll(delegate.changes(namespace, from))
        }
    }

    @Test
    fun `a replica that stays behind ends the stream with unavailable`() = runBlocking<Unit> {
        val store = MemoryStore()
        val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)
        val caches = WatchCache(StalledStore(store), WINDOW, scope)
        val properties = CoSkyProperties(read = CoSkyProperties.Read(minRevisionTimeout = Duration.ofMillis(100)))
        val name = UUID.randomUUID().toString()
        val server = InProcessServerBuilder.forName(name)
            .addService(WatchGrpcService(caches, properties))
            .build()
            .start()
        val channel = InProcessChannelBuilder.forName(name).build()
        try {
            store.createNamespace(NAMESPACE)
            val epoch = caches.of(NAMESPACE).awaitView().epoch
            val config = resource {
                meta = objectMeta {
                    namespace = NAMESPACE
                    this.name = "a"
                }
                this.config = configSpec {
                    format = ConfigFormat.CONFIG_FORMAT_TEXT
                    content = "a"
                }
            }
            store.apply(NAMESPACE, listOf(Mutation.Put(config)), "test")
            val create = WatchRequest.newBuilder().apply {
                createBuilder
                    .setWatchId(1)
                    .setNamespace(NAMESPACE)
                    .setKind(Kind.KIND_CONFIG)
                    .setFromRevision(1)
                    .setFromEpoch(epoch)
            }.build()
            val stub = WatchServiceGrpcKt.WatchServiceCoroutineStub(channel)
            val requests = flow {
                emit(create)
                CompletableDeferred<Unit>().await()
            }
            val failure = withTimeout(10.seconds) {
                runCatching { stub.watch(requests).first() }
            }.exceptionOrNull() as StatusException
            failure.status.code.assert().isEqualTo(Status.Code.UNAVAILABLE)
        } finally {
            channel.shutdownNow()
            server.shutdownNow()
            scope.cancel()
        }
    }

    @Test
    fun `a watch id still in use is refused, and the watch it names carries on`() = runBlocking<Unit> {
        val store = MemoryStore()
        val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)
        val caches = WatchCache(store, WINDOW, scope)
        val name = UUID.randomUUID().toString()
        val server = InProcessServerBuilder.forName(name)
            .addService(WatchGrpcService(caches, CoSkyProperties()))
            .build()
            .start()
        val channel = InProcessChannelBuilder.forName(name).build()
        try {
            store.createNamespace(NAMESPACE)
            val create = WatchRequest.newBuilder().apply {
                createBuilder.setWatchId(1).setNamespace(NAMESPACE).setKind(Kind.KIND_CONFIG)
            }.build()
            val cancel = WatchRequest.newBuilder().apply { cancelBuilder.setWatchId(1) }.build()
            val requests = Channel<WatchRequest>(Channel.UNLIMITED)
            withTimeout(10.seconds) {
                val responses = WatchServiceGrpcKt.WatchServiceCoroutineStub(channel)
                    .watch(requests.receiveAsFlow())
                    .produceIn(this)
                requests.send(create)
                responses.receive().payloadCase.assert().isEqualTo(WatchResponse.PayloadCase.SNAPSHOT)
                requests.send(create)
                responses.receive().payloadCase.assert().isEqualTo(WatchResponse.PayloadCase.CANCELED)

                store.apply(NAMESPACE, listOf(Mutation.Put(config())), "test")
                responses.receive().payloadCase.assert().isEqualTo(WatchResponse.PayloadCase.EVENTS)

                requests.send(cancel)
                requests.send(create)
                responses.receive().payloadCase.assert().isEqualTo(WatchResponse.PayloadCase.SNAPSHOT)
                responses.cancel()
            }
        } finally {
            channel.shutdownNow()
            server.shutdownNow()
            scope.cancel()
        }
    }

    private fun config() = resource {
        meta = objectMeta {
            namespace = NAMESPACE
            name = "a"
        }
        config = configSpec {
            format = ConfigFormat.CONFIG_FORMAT_TEXT
            content = "a"
        }
    }

    private companion object {
        const val NAMESPACE = "behind"
        const val WINDOW = 16
    }
}
