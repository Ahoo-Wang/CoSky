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

package me.ahoo.cosky.server.resource

import me.ahoo.cosky.api.kind
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.NamespaceState
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.key
import org.slf4j.LoggerFactory

/**
 * Compares the Namespace objects with the state of each namespace, for what no request is left to fix (design §7.2).
 * The object is written after the namespace is created and deleted after it is dropped, so any difference that lasts
 * means a deletion was interrupted, or the shard lost its data.
 */
class NamespaceReconciler(
    private val store: Store,
    private val caches: WatchCache,
    private val bootstrap: StoreBootstrap,
) {
    suspend fun reconcile() {
        if (store.namespaceState(NamingRules.SYSTEM_NAMESPACE) == null) {
            log.error("The system namespace has no state: the store lost its data. Setting it up as on a first start.")
            bootstrap.run()
            return
        }
        caches.of(NamingRules.SYSTEM_NAMESPACE).awaitView().objects.values
            .filter { it.kind == Kind.KIND_NAMESPACE }
            .forEach { namespace ->
                val name = namespace.meta.name
                when (store.namespaceState(name)) {
                    NamespaceState.ACTIVE -> Unit

                    NamespaceState.TERMINATING -> {
                        log.info("Finishing the deletion of namespace {}.", name)
                        store.dropNamespace(name)
                        store.apply(NamingRules.SYSTEM_NAMESPACE, listOf(Mutation.Delete(namespace.key)), ACTOR)
                    }

                    null -> {
                        log.error("Namespace {} has no state: its shard lost data. Creating it anew.", name)
                        store.createNamespace(name)
                    }
                }
            }
    }

    private companion object {
        const val ACTOR = "system"
        val log = LoggerFactory.getLogger(NamespaceReconciler::class.java)
    }
}
