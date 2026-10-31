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
import kotlinx.coroutines.launch
import me.ahoo.cosky.store.Store
import java.util.concurrent.ConcurrentHashMap

/**
 * The namespaces this server follows; each starts on first use.
 */
class WatchCache(
    private val store: Store,
    private val windowSize: Int,
    private val scope: CoroutineScope,
) {
    private val caches = ConcurrentHashMap<String, NamespaceCache>()

    fun of(namespace: String): NamespaceCache = caches.computeIfAbsent(namespace) {
        NamespaceCache(namespace, store, windowSize).also { cache ->
            scope.launch {
                cache.run()
                caches.remove(namespace, cache)
            }
        }
    }
}
