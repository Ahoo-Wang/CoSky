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

import com.google.protobuf.util.Timestamps
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.server.observe.CoSkyMetrics
import me.ahoo.cosky.store.ChangeBatch
import me.ahoo.cosky.store.ChangeType
import me.ahoo.cosky.store.Position
import me.ahoo.cosky.store.RevisionGoneException
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.TimelineDivergedException
import me.ahoo.cosky.store.key
import org.slf4j.LoggerFactory
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * One complete revision of a namespace, immutable, so that reads need no lock and always see a consistent state.
 *
 * @param earliest the oldest position a watcher can resume from.
 * @param window the revisions after [earliest], [windowEvents] changes and [windowBytes] in total (design D29, D36).
 * @param instancesByService what most reads ask for: a watch of one service must not scan the whole namespace, since
 * a replica answers one per client and service when it starts.
 */
data class NamespaceView(
    val epoch: String,
    val revision: Long,
    val earliest: Long,
    val objects: PersistentMap<ResourceKey, Resource>,
    val window: PersistentList<ChangeBatch>,
    val windowEvents: Int = window.sumOf { it.changes.size },
    val windowBytes: Long = window.sumOf { it.serializedSize },
    private val instancesByService: PersistentMap<String, PersistentMap<ResourceKey, Resource>> =
        indexByService(objects.values),
) {
    /**
     * The namespace is not active, or does not exist.
     */
    val isClosed: Boolean
        get() = this === CLOSED

    /**
     * The Instances of [service], in no particular order.
     */
    fun instancesOf(service: String): Collection<Resource> = instancesByService[service]?.values.orEmpty()

    fun applying(batch: ChangeBatch, windowSize: Int, maxWindowBytes: Long = Long.MAX_VALUE): NamespaceView {
        var applied = objects
        var index = instancesByService
        batch.changes.forEach { change ->
            val key = change.resource.key
            val current = applied[key]
            // Loading may have read this object after the change already; replaying it must not move it back.
            if ((current?.meta?.revision ?: 0) < batch.revision) {
                current?.let { index = index.without(it) }
                if (change.type == ChangeType.PUT) {
                    applied = applied.putting(key, change.resource)
                    index = index.with(change.resource)
                } else {
                    applied = applied.removing(key)
                }
            }
        }
        var trimmed = window.adding(batch)
        var events = windowEvents + batch.changes.size
        var bytes = windowBytes + batch.serializedSize
        // Whole revisions leave, the oldest first; the newest stays however large it is.
        while ((events > windowSize || bytes > maxWindowBytes) && trimmed.size > 1) {
            events -= trimmed.first().changes.size
            bytes -= trimmed.first().serializedSize
            trimmed = trimmed.removingAt(0)
        }
        return copy(
            revision = batch.revision,
            earliest = trimmed.first().revision - 1,
            objects = applied,
            window = trimmed,
            windowEvents = events,
            windowBytes = bytes,
            instancesByService = index,
        )
    }

    companion object {
        private fun indexByService(
            objects: Collection<Resource>,
        ): PersistentMap<String, PersistentMap<ResourceKey, Resource>> =
            objects.fold(persistentHashMapOf()) { index, resource -> index.with(resource) }

        private fun PersistentMap<String, PersistentMap<ResourceKey, Resource>>.with(
            resource: Resource,
        ): PersistentMap<String, PersistentMap<ResourceKey, Resource>> {
            if (!resource.hasInstance()) {
                return this
            }
            val service = resource.instance.service
            val instances = this[service] ?: persistentHashMapOf()
            return putting(service, instances.putting(resource.key, resource))
        }

        private fun PersistentMap<String, PersistentMap<ResourceKey, Resource>>.without(
            resource: Resource,
        ): PersistentMap<String, PersistentMap<ResourceKey, Resource>> {
            if (!resource.hasInstance()) {
                return this
            }
            val service = resource.instance.service
            val instances = this[service]?.removing(resource.key) ?: return this
            return if (instances.isEmpty()) removing(service) else putting(service, instances)
        }

        /**
         * Every wait for a revision ends on it, and it holds nothing.
         */
        val CLOSED = NamespaceView(
            epoch = "",
            revision = Long.MAX_VALUE,
            earliest = Long.MAX_VALUE,
            objects = persistentHashMapOf(),
            window = persistentListOf(),
        )
    }
}

/**
 * Follows one namespace in memory (design §6.1): a single coroutine owns the state and publishes immutable views.
 *
 * Watchers collect [views] themselves. A slow one simply misses intermediate views, and finds what it missed in the
 * window; it only has to start over when it falls out of the window.
 */
class NamespaceCache(
    private val namespace: String,
    private val store: Store,
    private val windowSize: Int,
    private val antiEntropyInterval: Duration = 5.minutes,
    private val metrics: CoSkyMetrics = CoSkyMetrics(),
    private val windowBytes: Long = Long.MAX_VALUE,
) {
    private val state = MutableStateFlow<NamespaceView?>(null)

    /**
     * How far this cache got, which outlives a reload: watchers are at most this far.
     */
    private var followed: Position? = null

    /**
     * Null while loading. A watcher waits that out: what ends it is the next view, if its position is no longer valid
     * there. So a failed read of the store does not make every client List again.
     */
    val views: StateFlow<NamespaceView?>
        get() = state

    /**
     * Whether it is loading because the timeline forked, in which case reads must not be served (design §6.4).
     */
    @Volatile
    var isDiverged: Boolean = false
        private set

    suspend fun awaitView(): NamespaceView = views.filterNotNull().first()

    /**
     * The position in the store itself, which this cache may be behind.
     */
    suspend fun storePosition(): Position? = store.position(namespace)

    /**
     * Runs until the namespace stops being active.
     */
    suspend fun run(): Unit = coroutineScope {
        val checking = launch { keepChecking() }
        follow()
        checking.cancel()
    }

    private suspend fun keepChecking() {
        while (true) {
            // Replicas should not all scan at once.
            delay(antiEntropyInterval * (JITTER_FLOOR + Random.nextDouble() * JITTER_SPREAD))
            try {
                checkAgainstStore()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                log.warn("Comparing namespace {} with the store failed.", namespace, e)
            }
        }
    }

    /**
     * Anti-entropy (design §6.1): no defect on the incremental path should last. What changed during the scan is
     * skipped, and any other difference counts as a forked timeline, which every replica and watcher then reloads.
     *
     * @return whether a difference was found.
     */
    suspend fun checkAgainstStore(): Boolean {
        val before = store.position(namespace) ?: return false
        val scanned = store.versions(namespace).toList().associate { it.key to it.revision }
        val after = store.position(namespace) ?: return false
        val view = withTimeoutOrNull(CATCH_UP_TIMEOUT) {
            state.first { it != null && (it.epoch != before.epoch || it.revision >= after.revision) }
        }
        // Without the changes since the scan began, nothing can be said this time.
        if (view == null || view.epoch != before.epoch || view.earliest > before.revision) {
            return false
        }
        val changed = view.window.filter { it.revision > before.revision }
            .flatMapTo(mutableSetOf()) { batch -> batch.changes.map { it.resource.key } }
        val different = (scanned.keys + view.objects.keys).count { key ->
            key !in changed && scanned[key] != view.objects[key]?.meta?.revision
        }
        if (different == 0) {
            return false
        }
        log.error("{} objects of namespace {} differ from the store; replacing the epoch.", different, namespace)
        metrics.antiEntropyDrift(namespace)
        store.rotateEpoch(namespace, view.epoch)
        return true
    }

    private suspend fun follow() {
        while (true) {
            try {
                if (!followOnce()) {
                    state.value = NamespaceView.CLOSED
                    return
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                log.warn("Following namespace {} failed; loading it again.", namespace, e)
                delay(RETRY_DELAY)
            }
        }
    }

    /**
     * @return whether to load again.
     */
    private suspend fun followOnce(): Boolean {
        state.value = null
        val position = store.position(namespace) ?: return false
        if (wentBack(position)) {
            isDiverged = true
            metrics.revisionRegression()
            log.error("Namespace {} went back from {} to {}; replacing the epoch.", namespace, followed, position)
            store.rotateEpoch(namespace, position.epoch)
            return true
        }
        state.value = load(position)
        isDiverged = false
        followed = position
        return try {
            store.changes(namespace, position).collect { batch ->
                state.update { it?.applying(batch, windowSize, windowBytes) }
                followed = Position(position.epoch, batch.revision)
                val writtenAt = batch.changes.first().resource.meta.updatedAt
                val lag = System.currentTimeMillis() - Timestamps.toMillis(writtenAt)
                metrics.cacheApplied(namespace, batch.revision, maxOf(lag, 0))
            }
            false
        } catch (e: TimelineDivergedException) {
            isDiverged = true
            metrics.revisionRegression()
            log.error("Namespace {} diverged, now at {}; loading it again.", namespace, e.current)
            true
        } catch (e: RevisionGoneException) {
            log.warn("Namespace {} fell behind the log, oldest position {}; loading it again.", namespace, e.earliest)
            true
        }
    }

    /**
     * A read that failed ended the tail, whose job it is to notice this (design §6.4). Loading the lower position under
     * the same epoch would leave watchers ahead of it, skipping the revisions that get reused.
     */
    private fun wentBack(position: Position): Boolean {
        val previous = followed ?: return false
        return previous.epoch == position.epoch && position.revision < previous.revision
    }

    private suspend fun load(position: Position): NamespaceView {
        val objects = store.readAll(namespace).fold(HashMap<ResourceKey, Resource>()) { all, resource ->
            all[resource.key] = resource
            all
        }
        val recent = store.recentChanges(namespace, position.revision, windowSize, windowBytes)
        return NamespaceView(
            epoch = position.epoch,
            revision = position.revision,
            earliest = recent.firstOrNull()?.let { it.revision - 1 } ?: position.revision,
            objects = objects.toPersistentHashMap(),
            window = recent.toPersistentList(),
        )
    }

    private companion object {
        val log = LoggerFactory.getLogger(NamespaceCache::class.java)
        val RETRY_DELAY = 1.seconds
        val CATCH_UP_TIMEOUT = 10.seconds
        const val JITTER_FLOOR = 0.8
        const val JITTER_SPREAD = 0.4
    }
}
