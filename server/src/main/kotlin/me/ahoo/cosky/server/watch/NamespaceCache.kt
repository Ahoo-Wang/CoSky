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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentHashMap
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
 * @param generation grows with every load: a watcher from an older one must start over.
 */
data class NamespaceView(
    val generation: Long,
    val epoch: String,
    val revision: Long,
    val earliest: Long,
    val objects: PersistentMap<ResourceKey, Resource>,
    val window: PersistentList<ChangeBatch>,
) {
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
        val next = window.adding(batch)
        val trimmed = if (next.size > windowSize) next.removingAt(0) else next
        return copy(
            revision = batch.revision,
            earliest = trimmed.first().revision - 1,
            objects = applied,
            window = trimmed,
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
    private val closed = MutableStateFlow(false)
    private var generation = 0L

    /**
     * Null while loading, which ends every watcher of the previous view.
     */
    val views: StateFlow<NamespaceView?>
        get() = state

    val isClosed: StateFlow<Boolean>
        get() = closed

    suspend fun awaitView(): NamespaceView = views.filterNotNull().first()

    /**
     * Runs until the namespace stops being active.
     */
    suspend fun run() {
        while (true) {
            try {
                if (!followOnce()) {
                    closed.value = true
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
        return try {
            store.changes(namespace, position).collect { batch ->
                state.update { it?.applying(batch, windowSize) }
            }
            false
        } catch (e: TimelineDivergedException) {
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
        generation += 1
        return NamespaceView(
            generation = generation,
            epoch = position.epoch,
            revision = position.revision,
            earliest = position.revision,
            objects = objects.toPersistentHashMap(),
            window = persistentListOf(),
        )
    }

    private companion object {
        val log = LoggerFactory.getLogger(NamespaceCache::class.java)
        val RETRY_DELAY = 1.seconds
    }
}
