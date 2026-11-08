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
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.NamespaceState
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.TerminateResult
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Compares the Namespace objects with the state of each namespace, for what no request is left to fix (design §7.2).
 * The object is written after the namespace is created, marked as terminating before a deletion touches anything, and
 * deleted after the namespace is dropped, so any difference that lasts means a deletion was interrupted, or the shard
 * lost its data.
 *
 * @param markedGrace how long a deletion that marked its object is left to its own request, which is still under way
 * most of the time: finishing it from here as well would race with it.
 */
class NamespaceReconciler(
    private val store: Store,
    private val caches: WatchCache,
    private val bootstrap: StoreBootstrap,
    private val clock: Clock = Clock.systemUTC(),
    private val markedGrace: Duration = Duration.ofMinutes(1),
    private val namespaces: NamespaceLifecycle = NamespaceLifecycle(store, caches),
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
                val terminating = namespace.namespace.terminating
                when (store.namespaceState(name)) {
                    NamespaceState.ACTIVE -> if (terminating && isLeftBehind(namespace)) finishOrUndo(namespace)

                    NamespaceState.TERMINATING -> finish(namespace)

                    // Whatever a namespace on its way out lost, it is deleted all the same.
                    null -> if (terminating) {
                        namespaces.deleteObject(namespace)
                    } else {
                        log.error("Namespace {} has no state: its shard lost data. Creating it anew.", name)
                        store.createNamespace(name)
                    }
                }
            }
    }

    private fun isLeftBehind(namespace: Resource): Boolean {
        val marked = namespace.meta.updatedAt.let { Instant.ofEpochSecond(it.seconds, it.nanos.toLong()) }
        return !marked.plus(markedGrace).isAfter(clock.instant())
    }

    /**
     * A deletion stopped between its mark and the termination: the namespace may have taken something since its
     * request looked, and then the deletion is undone.
     */
    private suspend fun finishOrUndo(namespace: Resource) {
        if (store.terminateNamespace(namespace.meta.name) == TerminateResult.TERMINATED) {
            finish(namespace)
            return
        }
        log.info("Namespace {} is not empty: undoing a deletion that stopped half way.", namespace.meta.name)
        namespaces.unmark(namespace, namespace.meta.revision)
    }

    private suspend fun finish(namespace: Resource) {
        log.info("Finishing the deletion of namespace {}.", namespace.meta.name)
        namespaces.finish(namespace)
    }

    private companion object {
        val log = LoggerFactory.getLogger(NamespaceReconciler::class.java)
    }
}
