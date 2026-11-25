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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.DeleteRequest
import me.ahoo.cosky.api.v1.Endpoint
import me.ahoo.cosky.api.v1.ErrorReason
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.PutRequest
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.Subject
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.api.v1.instanceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.server.auth.Authorizer
import me.ahoo.cosky.server.auth.Caller
import me.ahoo.cosky.server.auth.Grant
import me.ahoo.cosky.server.auth.Permission
import me.ahoo.cosky.server.auth.Principal
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.ApplyResult
import me.ahoo.cosky.store.ChangeBatch
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.Position
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.key
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * An Instance is authorized by its service, the one it has and the one it replaces (design §7.8). What it replaces is
 * what the write applies to, also where the replica that serves it is behind, or a write comes in between.
 */
class InstanceAuthorizationTest {
    private val store = MemoryStore()
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)

    /** While false, the replica sees no write. */
    private val following = MutableStateFlow(true)

    /** Once set, the next write has another service's Instance `a` written just before it. */
    private val racing = AtomicBoolean()

    private val replicated = object : Store by store {
        override fun changes(namespace: String, from: Position): Flow<ChangeBatch> =
            store.changes(namespace, from).onEach { following.first { it } }

        override suspend fun apply(namespace: String, mutations: List<Mutation>, actor: String): ApplyResult {
            if (racing.compareAndSet(true, false)) {
                store.apply(namespace, listOf(Mutation.Put(instance("a", "payment"), Mutation.NO_CHECK)), "other")
            }
            return store.apply(namespace, mutations, actor)
        }
    }
    private val caches = WatchCache(replicated, WINDOW, scope)

    /** May write and delete the Instances of `order`, and of no other service. */
    private val orderOnly = object : Authorizer {
        override fun permission(principal: Principal, verb: Verb, kind: Kind, namespace: String): Permission =
            if (kind == Kind.KIND_INSTANCE) Permission.Names(setOf("order")) else Permission.None

        override suspend fun requireHeld(principal: Principal, grant: Grant) = Unit

        override fun isValid(principal: Principal): Boolean = true

        override val changes: Flow<Unit> = emptyFlow()
    }
    private val resources =
        ResourceService(replicated, caches, minRevisionTimeout = SHORT_TIMEOUT, authorizer = orderOnly)

    @AfterEach
    fun stop() {
        scope.cancel()
    }

    private fun scenario(block: suspend CoroutineScope.() -> Unit): Unit = runBlocking(VirtualThreads.dispatcher) {
        withTimeout(10.seconds) {
            store.createNamespace(NAMESPACE)
            // The replica has its first view before it falls behind.
            caches.viewAt(NAMESPACE, 0, SHORT_TIMEOUT)
            block()
        }
    }

    private fun instance(name: String, service: String): Resource = resource {
        meta = objectMeta {
            namespace = NAMESPACE
            this.name = name
        }
        instance = instanceSpec {
            this.service = service
            endpoint = Endpoint.newBuilder().setScheme("http").setHost("localhost").setPort(8080).build()
        }
    }

    private suspend fun put(resource: Resource) =
        resources.put(PutRequest.newBuilder().setResource(resource).build(), CALLER)

    private suspend fun delete(name: String) =
        resources.delete(DeleteRequest.newBuilder().setKey(instance(name, "order").key).build(), CALLER)

    private suspend fun errorOf(block: suspend () -> Unit): ErrorReason =
        (runCatching { block() }.exceptionOrNull() as CoSkyException).reason

    private suspend fun stored(name: String): Resource? =
        caches.latestViewAt(NAMESPACE, SHORT_TIMEOUT).objects[instance(name, "order").key]

    @Test
    fun `a write that expects no revision is judged by what the store holds, not by a replica that is behind`() =
        scenario {
            following.value = false
            store.apply(NAMESPACE, listOf(Mutation.Put(instance("a", "payment"), Mutation.NO_CHECK)), "other")

            errorOf { put(instance("a", "order")) }.assert().isEqualTo(ErrorReason.ERROR_REASON_MIN_REVISION_TIMEOUT)
            following.value = true
            errorOf { put(instance("a", "order")) }.assert().isEqualTo(ErrorReason.ERROR_REASON_PERMISSION_DENIED)
            stored("a")?.instance?.service.assert().isEqualTo("payment")
        }

    @Test
    fun `a delete that expects no revision is judged by what the store holds, not by a replica that is behind`() =
        scenario {
            following.value = false
            store.apply(NAMESPACE, listOf(Mutation.Put(instance("a", "payment"), Mutation.NO_CHECK)), "other")

            errorOf { delete("a") }.assert().isEqualTo(ErrorReason.ERROR_REASON_MIN_REVISION_TIMEOUT)
            following.value = true
            errorOf { delete("a") }.assert().isEqualTo(ErrorReason.ERROR_REASON_PERMISSION_DENIED)
            stored("a")?.instance?.service.assert().isEqualTo("payment")
        }

    @Test
    fun `a write in between has the Instance judged again`() = scenario {
        racing.set(true)

        errorOf { put(instance("a", "order")) }.assert().isEqualTo(ErrorReason.ERROR_REASON_PERMISSION_DENIED)
        stored("a")?.instance?.service.assert().isEqualTo("payment")
    }

    @Test
    fun `a delete with a write in between has the Instance judged again`() = scenario {
        racing.set(true)

        errorOf { delete("a") }.assert().isEqualTo(ErrorReason.ERROR_REASON_PERMISSION_DENIED)
        stored("a")?.instance?.service.assert().isEqualTo("payment")
    }

    @Test
    fun `a revision the store has not reached is refused at once, with the one the store holds`() = scenario {
        following.value = false
        val written = store.apply(NAMESPACE, listOf(Mutation.Put(instance("a", "order"), 0)), "other")
        val revision = (written as ApplyResult.Applied).revision
        val unreachable = DeleteRequest.newBuilder()
            .setKey(instance("a", "order").key)
            .setExpectedRevision(Long.MAX_VALUE)
            .build()

        val refusal = runCatching { resources.delete(unreachable, CALLER) }.exceptionOrNull() as CoSkyException

        refusal.reason.assert().isEqualTo(ErrorReason.ERROR_REASON_REVISION_CONFLICT)
        refusal.detail.revisionConflict.currentRevision.assert().isEqualTo(revision)
    }

    @Test
    fun `an Instance of the service it may write comes and goes as before`() = scenario {
        put(instance("b", "order")).changed.assert().isTrue()
        put(instance("b", "order").toBuilder().apply { instanceBuilder.weight = 0 }.build()).changed.assert().isTrue()
        delete("b").deleted.assert().isTrue()
        delete("b").deleted.assert().isFalse()
        stored("b").assert().isNull()
    }

    private companion object {
        const val NAMESPACE = "instances"
        const val WINDOW = 64
        val CALLER = Caller(Principal(Subject.Type.TYPE_USER, "someone"))
        val SHORT_TIMEOUT = 300.milliseconds
    }
}
