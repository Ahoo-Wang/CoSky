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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.DeleteRequest
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.PutRequest
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.namespaceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.CoSkyProperties
import me.ahoo.cosky.server.auth.Caller
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.ApplyResult
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.NamespaceState
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.TerminateResult
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A deletion, or the reconciler finishing one, that comes late must leave alone the namespace created again since
 * (#1155): every step after the mark is for the epoch the mark recorded.
 */
class NamespaceGenerationTest {
    private val memory = MemoryStore()
    private val pauses = ConcurrentHashMap<String, Pause>()

    /**
     * Stops before the steps a test names, once each, until the test lets it go on.
     */
    private val store: Store = object : Store by memory {
        override suspend fun terminateNamespace(namespace: String, epoch: String): TerminateResult {
            pauseAt(TERMINATE)
            return memory.terminateNamespace(namespace, epoch)
        }

        override suspend fun apply(namespace: String, mutations: List<Mutation>, actor: String): ApplyResult {
            if (namespace == NamingRules.SYSTEM_NAMESPACE && mutations.any { it is Mutation.Delete }) {
                pauseAt(DELETE_OBJECT)
            }
            return memory.apply(namespace, mutations, actor)
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)
    private val caches = WatchCache(store, 64, scope)
    private val resources = ResourceService(store, caches)
    private val reconciler = NamespaceReconciler(
        store,
        caches,
        StoreBootstrap(store, CoSkyProperties.Auth(bootstrapToken = "cosky_abc_def")),
        markedGrace = Duration.ZERO,
    )
    private val key = ResourceKey.newBuilder()
        .setNamespace(NamingRules.SYSTEM_NAMESPACE)
        .setKind(Kind.KIND_NAMESPACE)
        .setName(NAME)
        .build()

    private class Pause {
        val reached = CompletableDeferred<Unit>()
        val resumed = CompletableDeferred<Unit>()
    }

    private suspend fun pauseAt(step: String) {
        val pause = pauses.remove(step) ?: return
        pause.reached.complete(Unit)
        pause.resumed.await()
    }

    private fun armed(step: String): Pause = Pause().also { pauses[step] = it }

    @AfterEach
    fun stop() {
        scope.cancel()
    }

    private suspend fun createNamespace() {
        store.createNamespace(NamingRules.SYSTEM_NAMESPACE)
        val namespace = resource {
            meta = objectMeta {
                namespace = NamingRules.SYSTEM_NAMESPACE
                name = NAME
            }
            this.namespace = namespaceSpec {}
        }
        resources.put(PutRequest.newBuilder().setResource(namespace).build(), Caller.ANONYMOUS)
    }

    private suspend fun writeConfig(): Resource {
        val config = resource {
            meta = objectMeta {
                namespace = NAME
                name = "order.yaml"
            }
            this.config = configSpec {
                format = ConfigFormat.CONFIG_FORMAT_YAML
                content = "limit: 1"
            }
        }
        resources.put(PutRequest.newBuilder().setResource(config).build(), Caller.ANONYMOUS)
        return config
    }

    private suspend fun stored(): Resource? = caches.latestViewAt(NamingRules.SYSTEM_NAMESPACE, 3.seconds).objects[key]

    private suspend fun eventually(condition: suspend () -> Boolean) = withTimeout(10.seconds) {
        while (!condition()) {
            delay(10.milliseconds)
        }
    }

    @Test
    fun `the reconciler finishing late leaves the object of a namespace created again`() = runBlocking<Unit> {
        createNamespace()
        val terminating = armed(TERMINATE)
        val deleting = async { resources.delete(DeleteRequest.newBuilder().setKey(key).build(), Caller.ANONYMOUS) }
        terminating.reached.await()
        eventually { stored()?.namespace?.terminating == true }
        // The reconciler takes the deletion over, and stops just before it deletes the object.
        val deletingObject = armed(DELETE_OBJECT)
        val reconciling = async { reconciler.reconcile() }
        deletingObject.reached.await()

        terminating.resumed.complete(Unit)
        deleting.await().deleted.assert().isTrue()
        createNamespace()
        writeConfig()
        deletingObject.resumed.complete(Unit)
        reconciling.await()

        stored()?.namespace?.terminating.assert().isEqualTo(false)
        store.namespaceState(NAME).assert().isEqualTo(NamespaceState.ACTIVE)
        caches.latestViewAt(NAME, 3.seconds).objects.assert().hasSize(1)
    }

    @Test
    fun `a late deletion leaves the namespace created again, and its history`() = runBlocking<Unit> {
        createNamespace()
        val terminating = armed(TERMINATE)
        val deleting = async {
            runCatching { resources.delete(DeleteRequest.newBuilder().setKey(key).build(), Caller.ANONYMOUS) }
        }
        terminating.reached.await()
        eventually { stored()?.namespace?.terminating == true }
        reconciler.reconcile()
        eventually { stored() == null }
        createNamespace()
        val config = writeConfig()
        val configKey = ResourceKey.newBuilder().setNamespace(NAME).setKind(Kind.KIND_CONFIG).setName("order.yaml")
            .build()
        resources.delete(DeleteRequest.newBuilder().setKey(configKey).build(), Caller.ANONYMOUS)
        val history = store.history(configKey, 0).size

        terminating.resumed.complete(Unit)

        deleting.await().isFailure.assert().isTrue()
        store.namespaceState(NAME).assert().isEqualTo(NamespaceState.ACTIVE)
        store.history(configKey, 0).size.assert().isEqualTo(history).isGreaterThan(0)
        stored()?.namespace?.terminating.assert().isEqualTo(false)
        config.meta.name.assert().isEqualTo("order.yaml")
    }

    private companion object {
        const val NAME = "orders"
        const val TERMINATE = "terminate"
        const val DELETE_OBJECT = "delete-object"
    }
}
