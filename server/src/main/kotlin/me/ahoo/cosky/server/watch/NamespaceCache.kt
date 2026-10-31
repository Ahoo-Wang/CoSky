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

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.update
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.store.ChangeBatch
import me.ahoo.cosky.store.ChangeType
import me.ahoo.cosky.store.Position
import me.ahoo.cosky.store.RevisionGoneException
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.TimelineDivergedException
import me.ahoo.cosky.store.key
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

/**
 * One complete revision of a namespace, immutable, so that reads need no lock and always see a consistent state.
 *
 * @param earliest the oldest position a watcher can resume from.
 * @param window the revisions after [earliest], [windowEvents] changes in total (design D29).
 */
data class NamespaceView(
    val epoch: String,
    val revision: Long,
    val earliest: Long,
    val objects: PersistentMap<ResourceKey, Resource>,
    val window: PersistentList<ChangeBatch>,
    val windowEvents: Int = window.sumOf { it.changes.size },
) {
    /**
     * The namespace is not active, or does not exist.
     */
    val isClosed: Boolean
        get() = this === CLOSED

    fun applying(batch: ChangeBatch, windowSize: Int): NamespaceView {
        val applied = batch.changes.fold(objects) { current, change ->
            val key = change.resource.key
            when {
                // Loading may have read this object after the change already; replaying it must not move it back.
                (current[key]?.meta?.revision ?: 0) >= batch.revision -> current
                change.type == ChangeType.PUT -> current.putting(key, change.resource)
                else -> current.removing(key)
            }
        }
        var trimmed = window.adding(batch)
        var events = windowEvents + batch.changes.size
        // Whole revisions leave, the oldest first; the newest stays however large it is.
        while (events > windowSize && trimmed.size > 1) {
            events -= trimmed.first().changes.size
            trimmed = trimmed.removingAt(0)
        }
        return copy(
            revision = batch.revision,
            earliest = trimmed.first().revision - 1,
            objects = applied,
            window = trimmed,
            windowEvents = events,
        )
    }

    companion object {
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
) {
    private val state = MutableStateFlow<NamespaceView?>(null)

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
    suspend fun run() {
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
        state.value = load(position)
        isDiverged = false
        return try {
            store.changes(namespace, position).collect { batch ->
                state.update { it?.applying(batch, windowSize) }
            }
            false
        } catch (e: TimelineDivergedException) {
            isDiverged = true
            log.error("Namespace {} diverged, now at {}; loading it again.", namespace, e.current)
            true
        } catch (e: RevisionGoneException) {
            log.warn("Namespace {} fell behind the log, oldest position {}; loading it again.", namespace, e.earliest)
            true
        }
    }

    private suspend fun load(position: Position): NamespaceView {
        val objects = store.readAll(namespace).fold(HashMap<ResourceKey, Resource>()) { all, resource ->
            all[resource.key] = resource
            all
        }
        val recent = store.recentChanges(namespace, position.revision, windowSize)
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
    }
}
