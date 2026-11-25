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
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.ErrorReason
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.Subject
import me.ahoo.cosky.api.v1.TokenRef
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.putRequest
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.v1.serviceAccountSpec
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.auth.Caller
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tokens are not for writing: a ServiceAccount keeps the ones the store holds, whatever the request says, and the
 * replica that serves the write must not hand it any it has not yet seen go (design §7.8).
 */
class ServiceAccountTokensTest {
    private val store = MemoryStore()
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)

    /** While false, the replica sees no write. */
    private val following = MutableStateFlow(true)

    private val behind = object : Store by store {
        override fun changes(namespace: String, from: Position): Flow<ChangeBatch> =
            store.changes(namespace, from).onEach { following.first { it } }
    }
    private val caches = WatchCache(behind, WINDOW, scope)
    private val replica = ResourceService(behind, caches, minRevisionTimeout = SHORT_TIMEOUT)

    @AfterEach
    fun stop() {
        scope.cancel()
    }

    private fun scenario(block: suspend CoroutineScope.() -> Unit): Unit = runBlocking(VirtualThreads.dispatcher) {
        withTimeout(10.seconds) {
            store.createNamespace(NamingRules.SYSTEM_NAMESPACE)
            block()
        }
    }

    private fun account(vararg tokens: String): Resource = resource {
        meta = objectMeta {
            namespace = NamingRules.SYSTEM_NAMESPACE
            name = "app"
        }
        serviceAccount = serviceAccountSpec {
            tokens.forEach { this.tokens += TokenRef.newBuilder().setId(it).setSha256("00").build() }
        }
    }

    /** Writes [resource] to the store, where the replica sees it only while following. */
    private suspend fun applied(resource: Resource, expected: Long): Long {
        val mutation = Mutation.Put(resource, expected)
        return (store.apply(NamingRules.SYSTEM_NAMESPACE, listOf(mutation), "test") as ApplyResult.Applied).revision
    }

    /** The replica has seen [revision]. */
    private suspend fun seen(revision: Long) {
        caches.viewAt(NamingRules.SYSTEM_NAMESPACE, revision, SHORT_TIMEOUT)
    }

    private suspend fun storedTokens(): List<String> =
        store.readAll(NamingRules.SYSTEM_NAMESPACE).toList().single().serviceAccount.tokensList.map { it.id }

    private suspend fun errorOf(block: suspend () -> Unit): ErrorReason =
        (runCatching { block() }.exceptionOrNull() as CoSkyException).reason

    @Test
    fun `a replica that is behind does not bring back a revoked token`() = scenario {
        val two = applied(account("kept", "revoked"), Mutation.NO_CHECK)
        seen(two)

        following.value = false
        val one = applied(account("kept"), two)
        errorOf {
            replica.put(
                putRequest {
                    resource = account()
                    expectedRevision = one
                },
                CALLER
            )
        }.assert().isEqualTo(ErrorReason.ERROR_REASON_MIN_REVISION_TIMEOUT)

        storedTokens().assert().containsExactly("kept")
    }

    @Test
    fun `an account created again on a replica that is behind does not get the tokens of the deleted one`() = scenario {
        seen(applied(account("leaked"), Mutation.NO_CHECK))

        following.value = false
        store.apply(NamingRules.SYSTEM_NAMESPACE, listOf(Mutation.Delete(account().key)), "test")
        replica.put(
            putRequest {
                resource = account()
                expectedRevision = Mutation.MUST_NOT_EXIST
            },
            CALLER
        )

        storedTokens().assert().isEmpty()
    }

    @Test
    fun `an account written without a revision on a replica that is behind keeps the tokens it has now`() = scenario {
        val two = applied(account("kept", "revoked"), Mutation.NO_CHECK)
        seen(two)

        following.value = false
        applied(account("kept"), two)
        // Read then, the replica's view would expect a revision that is gone, and the write would fail as a conflict.
        val put = async { replica.put(putRequest { resource = account() }, CALLER) }
        delay(SHORT_TIMEOUT / 3)
        following.value = true
        put.await()

        storedTokens().assert().containsExactly("kept")
    }

    private companion object {
        const val WINDOW = 64
        val CALLER = Caller(Principal(Subject.Type.TYPE_USER, "someone"))
        val SHORT_TIMEOUT = 300.milliseconds
    }
}
