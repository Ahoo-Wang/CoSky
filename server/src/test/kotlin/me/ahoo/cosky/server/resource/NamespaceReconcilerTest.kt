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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.kind
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.namespaceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.CoSkyProperties
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.NamespaceState
import me.ahoo.cosky.store.TerminateResult
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class NamespaceReconcilerTest {
    private val store = MemoryStore()
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)
    private val caches = WatchCache(store, 64, scope)
    private val auth = CoSkyProperties.Auth(bootstrapToken = "cosky_abc_def")
    private val reconciler = NamespaceReconciler(store, caches, StoreBootstrap(store, auth))

    @AfterEach
    fun stop() {
        scope.cancel()
    }

    private suspend fun objectOf(name: String, terminating: Boolean = false) {
        store.createNamespace(NamingRules.SYSTEM_NAMESPACE)
        val namespace = resource {
            meta = objectMeta {
                namespace = NamingRules.SYSTEM_NAMESPACE
                this.name = name
            }
            this.namespace = namespaceSpec { this.terminating = terminating }
        }
        store.apply(NamingRules.SYSTEM_NAMESPACE, listOf(Mutation.Put(namespace)), "test")
        caches.of(NamingRules.SYSTEM_NAMESPACE).views.filterNotNull().first { view ->
            view.objects.values.any { it.kind == Kind.KIND_NAMESPACE && it.meta.name == name }
        }
    }

    private suspend fun eventually(condition: suspend () -> Boolean) {
        while (!condition()) {
            delay(10.milliseconds)
        }
    }

    @Test
    fun `an active namespace is left alone`() = runBlocking<Unit> {
        store.createNamespace(NAME)
        objectOf(NAME)
        val before = store.position(NAME)
        reconciler.reconcile()
        store.position(NAME).assert().isEqualTo(before)
    }

    @Test
    fun `an interrupted deletion is finished`() = runBlocking<Unit> {
        store.createNamespace(NAME)
        objectOf(NAME)
        store.terminateNamespace(NAME).assert().isEqualTo(TerminateResult.TERMINATED)
        reconciler.reconcile()
        store.namespaceState(NAME).assert().isEqualTo(NamespaceState.TERMINATING)
        eventually {
            caches.of(NamingRules.SYSTEM_NAMESPACE).awaitView().objects.values.none { it.meta.name == NAME }
        }
    }

    @Test
    fun `a store that lost everything is set up as on a first start`() = runBlocking<Unit> {
        store.namespaceState(NamingRules.SYSTEM_NAMESPACE).assert().isNull()
        reconciler.reconcile()
        store.namespaceState(NamingRules.SYSTEM_NAMESPACE).assert().isEqualTo(NamespaceState.ACTIVE)
        store.namespaceState("default").assert().isEqualTo(NamespaceState.ACTIVE)
        store.readAll(NamingRules.SYSTEM_NAMESPACE).toList().map { it.kind }
            .assert().contains(Kind.KIND_NAMESPACE, Kind.KIND_SERVICE_ACCOUNT, Kind.KIND_ROLE_BINDING)
    }

    @Test
    fun `a namespace that lost its state is created anew`() = runBlocking<Unit> {
        objectOf(NAME)
        store.namespaceState(NAME).assert().isNull()
        reconciler.reconcile()
        store.namespaceState(NAME).assert().isEqualTo(NamespaceState.ACTIVE)
    }

    private suspend fun objectOrNull(name: String): Resource? =
        store.readAll(NamingRules.SYSTEM_NAMESPACE).toList().singleOrNull { it.meta.name == name }

    /** One that finds every marked deletion left behind, however recent. */
    private val impatient = NamespaceReconciler(store, caches, StoreBootstrap(store, auth), markedGrace = Duration.ZERO)

    @Test
    fun `a deletion that stopped after its mark is finished when the namespace is empty`() = runBlocking<Unit> {
        store.createNamespace(NAME)
        objectOf(NAME, terminating = true)

        impatient.reconcile()

        store.namespaceState(NAME).assert().isEqualTo(NamespaceState.TERMINATING)
        objectOrNull(NAME).assert().isNull()
    }

    @Test
    fun `a deletion that stopped after its mark is undone when the namespace holds something`() = runBlocking<Unit> {
        store.createNamespace(NAME)
        store.apply(NAME, listOf(Mutation.Put(config("kept"))), "test")
        objectOf(NAME, terminating = true)

        impatient.reconcile()

        store.namespaceState(NAME).assert().isEqualTo(NamespaceState.ACTIVE)
        checkNotNull(objectOrNull(NAME)).namespace.terminating.assert().isFalse()
    }

    @Test
    fun `a deletion that marked its object a moment ago is left to its own request`() = runBlocking<Unit> {
        store.createNamespace(NAME)
        objectOf(NAME, terminating = true)

        reconciler.reconcile()

        store.namespaceState(NAME).assert().isEqualTo(NamespaceState.ACTIVE)
        checkNotNull(objectOrNull(NAME)).namespace.terminating.assert().isTrue()
    }

    @Test
    fun `a namespace on its way out that lost its state is deleted, not created anew`() = runBlocking<Unit> {
        objectOf(NAME, terminating = true)

        reconciler.reconcile()

        store.namespaceState(NAME).assert().isNull()
        objectOrNull(NAME).assert().isNull()
    }

    private fun config(name: String): Resource = resource {
        meta = objectMeta {
            namespace = NAME
            this.name = name
        }
        config = configSpec {
            format = ConfigFormat.CONFIG_FORMAT_TEXT
            content = "x"
        }
    }

    private companion object {
        const val NAME = "orders"
    }
}
