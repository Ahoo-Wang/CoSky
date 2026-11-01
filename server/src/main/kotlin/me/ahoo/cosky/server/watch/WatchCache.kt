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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.server.observe.CoSkyMetrics
import me.ahoo.cosky.store.Store
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

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

    fun of(namespace: String): NamespaceCache = caches.computeIfAbsent(namespace) {
        NamespaceCache(namespace, store, windowSize, antiEntropyInterval, metrics, windowBytes).also { cache ->
            scope.launch {
                cache.run()
                caches.remove(namespace, cache)
            }
        }
    }

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
            cache.views.value == null -> CoSkyException.storeUnavailable("Namespace $namespace is loading.")
            else -> CoSkyException.minRevisionTimeout(namespace, minRevision)
        }
    }
}
