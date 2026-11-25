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

package me.ahoo.cosky.server.watch

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.WatchRequest
import me.ahoo.cosky.api.v1.WatchResponse
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.server.CoSkyProperties
import me.ahoo.cosky.server.auth.Permission
import me.ahoo.cosky.server.auth.Principal
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The watches of one stream, as Cancel and Create of the same id come and go (design §4.3).
 */
class WatchStreamTest {
    private val store = MemoryStore()
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)
    private val caches = WatchCache(store, WINDOW, scope)
    private val watches = WatchService(caches, CoSkyProperties())

    @AfterEach
    fun stop() {
        scope.cancel()
    }

    private fun create(namespace: String): WatchRequest.Create =
        WatchRequest.Create.newBuilder().setWatchId(1).setNamespace(namespace).setKind(Kind.KIND_CONFIG).build()

    private fun config(namespace: String, name: String): Resource = resource {
        meta = objectMeta {
            this.namespace = namespace
            this.name = name
        }
        config = configSpec {
            format = ConfigFormat.CONFIG_FORMAT_TEXT
            content = name
        }
    }

    @Test
    fun `a watch that ends leaves alone the one a Cancel and a Create of its id made meanwhile`() =
        runBlocking<Unit>(VirtualThreads.dispatcher) {
            withTimeout(10.seconds) {
                store.createNamespace(OLD)
                store.createNamespace(NEW)
                val newEvents = Channel<WatchResponse>(Channel.UNLIMITED)
                val swapped = AtomicBoolean()
                val followed = CompletableDeferred<Unit>()
                lateinit var stream: WatchService.WatchStream
                stream = watches.stream(Principal.ANONYMOUS, null, scope) { response ->
                    when {
                        // The old watch's last message, sent from its own coroutine, which ends right after it
                        // without suspending again: the client cancels it and creates another of the same id then.
                        response.hasGone() && swapped.compareAndSet(false, true) -> {
                            stream.cancel(1)
                            stream.start(create(NEW), Permission.All)
                        }

                        response.hasSnapshot() && swapped.get() -> followed.complete(Unit)

                        response.hasEvents() -> newEvents.send(response)
                    }
                }
                // Loaded, so that the watch below ends with `gone` rather than with this replica not being ready.
                listOf(OLD, NEW).forEach { caches.of(it).awaitLoaded() }
                // A position on a timeline the namespace no longer has, which ends the watch with `gone` at once.
                val stale = create(OLD).toBuilder().setFromRevision(1).setFromEpoch("lost").build()
                stream.start(stale, Permission.All)
                followed.await()

                store.apply(NEW, listOf(Mutation.Put(config(NEW, "before"))), "test")
                newEvents.receive().events.eventsList.single().resource.meta.name.assert().isEqualTo("before")

                stream.cancel(1)
                store.apply(NEW, listOf(Mutation.Put(config(NEW, "after"))), "test")
                delay(SETTLE)
                newEvents.tryReceive().getOrNull().assert().isNull()
            }
        }

    private companion object {
        const val WINDOW = 16
        const val OLD = "old"
        const val NEW = "new"
        val SETTLE = 300.milliseconds
    }
}
