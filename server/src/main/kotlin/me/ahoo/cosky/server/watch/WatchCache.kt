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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.server.observe.CoSkyMetrics
import me.ahoo.cosky.store.Store
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The namespaces this server follows; each starts on first use.
 */
class WatchCache(
    private val store: Store,
    private val windowSize: Int,
    private val scope: CoroutineScope,
    private val antiEntropyInterval: Duration = 5.minutes,
    val metrics: CoSkyMetrics = CoSkyMetrics(),
    private val windowBytes: Long = Long.MAX_VALUE,
) {
    private val caches = ConcurrentHashMap<String, NamespaceCache>()
    private val followers = ConcurrentHashMap<String, Flow<NamespaceView>>()

    fun of(namespace: String): NamespaceCache = caches.computeIfAbsent(namespace) {
        NamespaceCache(namespace, store, windowSize, antiEntropyInterval, metrics, windowBytes).also { cache ->
            scope.launch {
                cache.run()
                // Before another cache of it can start, whose series would go too.
                metrics.namespaceGone(namespace)
                caches.remove(namespace, cache)
            }
        }
    }

    /**
     * The views of [namespace] for as long as they are collected, from whichever cache follows it. A cache stops when
     * its namespace stops being active, and a namespace created again, under a new epoch, as the system namespace is
     * after the store lost its data, has a cache of its own: collecting the views of one cache would stop there for
     * good. A namespace that is gone has no views, and is looked for again every second.
     *
     * Its collectors share one follower, so that a namespace that is gone is looked for once however many wait for it,
     * and each sees the latest view, as with a cache's own: a slow one misses those in between.
     */
    fun follow(namespace: String): Flow<NamespaceView> = followers.computeIfAbsent(namespace) {
        flow {
            while (true) {
                emitAll(of(namespace).views.filterNotNull().takeWhile { !it.isClosed })
                // The cache that stopped goes once it has; until then, of() still gives it.
                delay(FOLLOW_RETRY)
            }
        }.conflate().shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)
    }

    /**
     * A view once this replica caught up with the store's latest write, for a write that decides on what is there.
     *
     * @throws CoSkyException as [viewAt].
     */
    suspend fun latestViewAt(namespace: String, timeout: Duration): NamespaceView =
        viewAt(namespace, store.position(namespace)?.revision ?: 0, timeout)

    /**
     * A view to read at, no older than [minRevision].
     *
     * @throws CoSkyException if there is none to serve within [timeout], saying why.
     */
    suspend fun viewAt(namespace: String, minRevision: Long, timeout: Duration): NamespaceView {
        val cache = of(namespace)
        val view = withTimeoutOrNull(timeout) {
            cache.views.first { it != null && it.revision >= minRevision }
        }
        if (view != null && !view.isClosed) {
            return view
        }
        throw when {
            view != null -> CoSkyException.namespaceNotFound(namespace)
            cache.isDiverged -> CoSkyException.revisionRegression(namespace)
            cache.views.value == null -> CoSkyException.replicaNotReady(namespace, "Namespace $namespace is loading.")
            else -> CoSkyException.minRevisionTimeout(namespace, minRevision)
        }
    }

    private companion object {
        val FOLLOW_RETRY = 1.seconds
    }
}
