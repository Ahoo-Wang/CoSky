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

import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.ErrorReason
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.WatchRequest
import me.ahoo.cosky.api.v1.WatchResponse
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.store.Change
import me.ahoo.cosky.store.ChangeBatch
import me.ahoo.cosky.store.ChangeType
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.Position
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.key
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class WatchSessionTest {
    private val store = MemoryStore(keepRevisions = 16)
    private val cache = NamespaceCache(NAMESPACE, store, WINDOW)

    /**
     * Its changes do not reach a cache until released, as on a replica that is behind.
     */
    private class StalledStore(private val delegate: Store) : Store by delegate {
        val released = CompletableDeferred<Unit>()

        override fun changes(namespace: String, from: Position): Flow<ChangeBatch> = flow {
            released.await()
            emitAll(delegate.changes(namespace, from))
        }
    }

    /**
     * Its changes fail once when told to, as when the connection to the store breaks.
     */
    private class FailingStore(private val delegate: Store) : Store by delegate {
        val failure = CompletableDeferred<Unit>()
        private val failed = AtomicBoolean()

        override fun changes(namespace: String, from: Position): Flow<ChangeBatch> = merge(
            delegate.changes(namespace, from),
            flow {
                failure.await()
                check(failed.getAndSet(true)) { "The store is gone." }
                awaitCancellation()
            },
        )
    }

    private fun config(name: String, content: String = "a"): Resource = resource {
        meta = objectMeta {
            namespace = NAMESPACE
            this.name = name
        }
        config = configSpec {
            format = ConfigFormat.CONFIG_FORMAT_TEXT
            this.content = content
        }
    }

    private suspend fun put(name: String, content: String = "a") {
        store.apply(NAMESPACE, listOf(Mutation.Put(config(name, content))), "test")
    }

    private suspend fun caughtUpTo(revision: Long): NamespaceView =
        checkNotNull(cache.views.first { it?.revision == revision })

    private fun create(fromRevision: Long = 0, fromEpoch: String = "", name: String = ""): WatchRequest.Create =
        WatchRequest.Create.newBuilder()
            .setWatchId(1)
            .setNamespace(NAMESPACE)
            .setKind(Kind.KIND_CONFIG)
            .setName(name)
            .setFromRevision(fromRevision)
            .setFromEpoch(fromEpoch)
            .build()

    private fun scenario(
        cache: NamespaceCache = this.cache,
        block: suspend CoroutineScope.() -> Unit,
    ): Unit = runBlocking(VirtualThreads.dispatcher) {
        withTimeout(10.seconds) {
            store.createNamespace(NAMESPACE)
            val following = launch { cache.run() }
            block()
            following.cancel()
        }
    }

    /**
     * Runs a session until it has sent what [done] waits for, and returns everything it sent.
     */
    private suspend fun CoroutineScope.watch(
        create: WatchRequest.Create,
        maxBytes: Int = 1024 * 1024,
        cache: NamespaceCache = this@WatchSessionTest.cache,
        catchUpTimeout: Duration = 5.seconds,
        whileRunning: suspend () -> Unit = {},
        done: (WatchResponse) -> Boolean,
    ): List<WatchResponse> {
        val sent = CopyOnWriteArrayList<WatchResponse>()
        val finished = CompletableDeferred<Unit>()
        val session = launch {
            WatchSession(create, cache, maxBytes, catchUpTimeout) {
                sent += it
                if (done(it)) {
                    finished.complete(Unit)
                }
            }.run()
        }
        whileRunning()
        finished.await()
        session.cancel()
        return sent
    }

    @Test
    fun `a watch starts from a snapshot with its position, then follows`() = scenario {
        put("a")
        caughtUpTo(1)
        val sent = watch(create(), whileRunning = {
            delay(SETTLE)
            put("b")
        }) { it.hasEvents() }
        sent.first().snapshot.itemsList.map { it.meta.name }.assert().containsExactly("a")
        sent.first().snapshot.revision.assert().isEqualTo(1)
        sent.first().snapshot.epoch.assert().isNotEmpty()
        sent.last().events.revision.assert().isEqualTo(2)
    }

    @Test
    fun `a large snapshot arrives in chunks, marked by more`() = scenario {
        repeat(3) { put("c$it", "x".repeat(100)) }
        caughtUpTo(3)
        val sent = watch(create(), maxBytes = 200) { !it.snapshot.more }
        sent.map { it.snapshot.itemsCount }.assert().containsExactly(1, 1, 1)
        sent.map { it.snapshot.more }.assert().containsExactly(true, true, false)
        sent.map { it.snapshot.revision }.toSet().assert().containsExactly(3L)
    }

    @Test
    fun `an empty namespace still sends its position`() = scenario {
        cache.awaitView()
        val sent = watch(create()) { it.hasSnapshot() }
        sent.single().snapshot.itemsList.assert().isEmpty()
    }

    @Test
    fun `a watch resumes from a position on the same epoch`() = scenario {
        put("a")
        put("b")
        val view = caughtUpTo(2)
        val sent = watch(create(1, view.epoch)) { it.hasEvents() }
        sent.single().events.revision.assert().isEqualTo(2)
    }

    @Test
    fun `another epoch cannot resume`() = scenario {
        put("a")
        caughtUpTo(1)
        watch(create(1, "another")) { it.hasGone() }.single().hasGone().assert().isTrue()
    }

    @Test
    fun `a position ahead of this replica waits for it to catch up`() {
        val stalled = StalledStore(store)
        val behind = NamespaceCache(NAMESPACE, stalled, WINDOW)
        scenario(behind) {
            put("a")
            val view = behind.awaitView()
            repeat(2) { put("c$it") }
            val sent = watch(create(2, view.epoch), cache = behind, whileRunning = {
                delay(SETTLE)
                stalled.released.complete(Unit)
            }) { it.hasEvents() }
            sent.single().events.revision.assert().isEqualTo(3)
        }
    }

    @Test
    fun `a replica that stays behind cannot serve the position`() {
        val behind = NamespaceCache(NAMESPACE, StalledStore(store), WINDOW)
        scenario(behind) {
            put("a")
            val view = behind.awaitView()
            put("b")
            val session = WatchSession(create(2, view.epoch), behind, 1024, SETTLE) {}
            val error = runCatching { session.run() }.exceptionOrNull() as CoSkyException
            error.detail.reason.assert().isEqualTo(ErrorReason.ERROR_REASON_STORE_UNAVAILABLE)
        }
    }

    @Test
    fun `a position the store does not have either is gone`() = scenario {
        val view = cache.awaitView()
        val sent = watch(create(5, view.epoch), catchUpTimeout = SETTLE) { it.hasGone() }
        sent.single().hasGone().assert().isTrue()
    }

    @Test
    fun `a new epoch while waiting is gone`() = scenario {
        put("a")
        val view = caughtUpTo(1)
        val sent = watch(create(5, view.epoch), whileRunning = {
            delay(SETTLE)
            store.loseWritesAfter(NAMESPACE, 0)
        }) { it.hasGone() }
        sent.single().hasGone().assert().isTrue()
    }

    @Test
    fun `a replica can resume clients right after it loads`() = runBlocking<Unit> {
        store.createNamespace(NAMESPACE)
        repeat(3) { put("c$it") }
        scenario {
            val view = cache.awaitView()
            view.earliest.assert().isEqualTo(0)
            val sent = watch(create(1, view.epoch)) { it.events.revision == 3L }
            sent.map { it.events.revision }.assert().containsExactly(2L, 3L)
        }
    }

    @Test
    fun `reloading after a failed read does not end a watch on the same epoch`() {
        val failing = FailingStore(store)
        val reloading = NamespaceCache(NAMESPACE, failing, WINDOW)
        scenario(reloading) {
            put("a")
            val view = checkNotNull(reloading.views.first { it?.revision == 1L })
            val sent = watch(create(view.revision, view.epoch), cache = reloading, whileRunning = {
                delay(SETTLE)
                failing.failure.complete(Unit)
                reloading.views.first { it == null }
                put("b")
            }) { it.hasEvents() }
            sent.single().events.revision.assert().isEqualTo(view.revision + 1)
        }
    }

    @Test
    fun `a watch of a namespace that does not exist is canceled`() {
        val missing = NamespaceCache("missing", store, WINDOW)
        scenario(missing) {
            val sent = watch(create(), cache = missing) { it.hasCanceled() }
            sent.single().canceled.detail.reason.assert().isEqualTo(ErrorReason.ERROR_REASON_NAMESPACE_NOT_FOUND)
        }
    }

    @Test
    fun `a namespace that stops being active cancels its watches`() = scenario {
        cache.awaitView()
        val sent = watch(create(), whileRunning = {
            delay(SETTLE)
            store.terminateNamespace(NAMESPACE)
        }) { it.hasCanceled() }
        sent.last().canceled.detail.reason.assert().isEqualTo(ErrorReason.ERROR_REASON_NAMESPACE_NOT_FOUND)
    }

    @Test
    fun `a position outside the window cannot resume`() = scenario {
        repeat(WINDOW + 2) { put("c", "$it") }
        val view = caughtUpTo(WINDOW + 2L)
        watch(create(1, view.epoch)) { it.hasGone() }.single().gone.assert().isEqualTo(view.earliest)
    }

    @Test
    fun `lost writes end a running watch with gone, and the cache reloads`() = scenario {
        put("a")
        put("b")
        val view = caughtUpTo(2)
        val sent = watch(create(2, view.epoch), whileRunning = {
            delay(SETTLE)
            store.loseWritesAfter(NAMESPACE, 1)
        }) { it.hasGone() }
        sent.single().hasGone().assert().isTrue()
        val reloaded = checkNotNull(cache.views.first { it != null && it.epoch != view.epoch })
        reloaded.revision.assert().isEqualTo(1)
        reloaded.objects.values.single().meta.name.assert().isEqualTo("a")
    }

    @Test
    fun `a filter by name delivers only that object`() = scenario {
        put("other")
        put("wanted")
        caughtUpTo(2)
        val sent = watch(create(name = "wanted")) { it.hasSnapshot() }
        sent.single().snapshot.itemsList.map { it.meta.name }.assert().containsExactly("wanted")
    }

    @Test
    fun `replaying an older event does not move an object back`() {
        val newer = config("a").toBuilder().apply { metaBuilder.revision = 5 }.build()
        val older = config("a", "older").toBuilder().apply { metaBuilder.revision = 4 }.build()
        val view = NamespaceView(
            epoch = "e",
            revision = 3,
            earliest = 3,
            objects = persistentHashMapOf(newer.key to newer),
            window = persistentListOf(),
        )
        val applied = view.applying(ChangeBatch(4, listOf(Change(ChangeType.PUT, older))), WINDOW)
        applied.objects.getValue(newer.key).meta.revision.assert().isEqualTo(5)
        applied.revision.assert().isEqualTo(4)
    }

    @Test
    fun `the window counts changes, and whole revisions leave it`() {
        fun batch(revision: Long, changes: Int) =
            ChangeBatch(revision, List(changes) { Change(ChangeType.PUT, config("c$it")) })
        val empty = NamespaceView("e", 0, 0, persistentHashMapOf(), persistentListOf())

        val full = empty.applying(batch(1, 3), WINDOW).applying(batch(2, 1), WINDOW)
        full.window.map { it.revision }.assert().containsExactly(1L, 2L)
        full.earliest.assert().isEqualTo(0)

        val trimmed = full.applying(batch(3, 2), WINDOW)
        trimmed.window.map { it.revision }.assert().containsExactly(2L, 3L)
        trimmed.windowEvents.assert().isEqualTo(3)
        trimmed.earliest.assert().isEqualTo(1)

        val oversized = trimmed.applying(batch(4, WINDOW + 1), WINDOW)
        oversized.window.map { it.revision }.assert().containsExactly(4L)
        oversized.earliest.assert().isEqualTo(3)
    }

    @Test
    fun `chunks split by bytes and keep at least one item`() {
        listOf(100, 100, 300, 50).chunkedByBytes(200) { it }
            .assert().containsExactly(listOf(100, 100), listOf(300), listOf(50))
        emptyList<Int>().chunkedByBytes(200) { it }.assert().isEmpty()
    }

    @Test
    fun `carriers are virtual threads`() = runBlocking<Unit> {
        withContext(VirtualThreads.dispatcher) { Thread.currentThread().isVirtual }.assert().isTrue()
    }

    @Test
    fun `tests run on the JDK the server runs on`() {
        Runtime.version().feature().assert().isEqualTo(25)
    }

    private companion object {
        const val NAMESPACE = "watching"
        const val WINDOW = 4
        val SETTLE = 100.milliseconds
    }
}
