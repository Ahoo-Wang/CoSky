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

package me.ahoo.cosky.server.auth

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.ErrorReason
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.v1.serviceAccountSpec
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.ApplyResult
import me.ahoo.cosky.store.ChangeBatch
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.Position
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TokenServiceTest {
    private val store = MemoryStore()
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)
    private val caches = WatchCache(store, 16, scope)
    private val audit = LoggerFactory.getLogger("cosky.audit") as Logger
    private val lines = ListAppender<ILoggingEvent>()
    private val previousLevel = audit.level

    @BeforeEach
    fun start(): Unit = runBlocking {
        audit.level = Level.INFO
        lines.start()
        audit.addAppender(lines)
        store.createNamespace(NamingRules.SYSTEM_NAMESPACE)
        store.apply(NamingRules.SYSTEM_NAMESPACE, listOf(Mutation.Put(accountNamed(ACCOUNT))), "test")
    }

    private fun accountNamed(name: String): Resource = resource {
        meta = objectMeta {
            namespace = NamingRules.SYSTEM_NAMESPACE
            this.name = name
        }
        serviceAccount = serviceAccountSpec {}
    }

    @AfterEach
    fun stop() {
        audit.detachAppender(lines)
        audit.level = previousLevel
        scope.cancel()
    }

    @Test
    fun `creating and revoking a token leave an audit line, without its secret`() = runBlocking<Unit> {
        val tokens = TokenService(store, caches)
        val created = tokens.create(ACCOUNT, null, Caller(Principal.ANONYMOUS, "10.0.0.1")).token
        tokens.revoke(ACCOUNT, created.id, Caller.ANONYMOUS)

        val (create, revoke) = lines.list.map { it.formattedMessage }
        create.assert().contains("\"verb\":\"PUT\"", "\"kind\":\"SERVICE_ACCOUNT\"", "\"result\":\"OK\"")
        create.assert().contains("\"name\":\"$ACCOUNT/tokens/${created.id}\"", "\"remote\":\"10.0.0.1\"")
        create.assert().doesNotContain(created.secret)
        revoke.assert().contains("\"verb\":\"DELETE\"", "\"result\":\"OK\"")
    }

    @Test
    fun `a token change answers the account's revision, which a later change can expect`() = runBlocking<Unit> {
        val tokens = TokenService(store, caches)
        val created = tokens.create(ACCOUNT, null, Caller.ANONYMOUS)
        created.revision.assert().isEqualTo(checkNotNull(store.position(NamingRules.SYSTEM_NAMESPACE)).revision)

        val stale = runCatching { tokens.create(ACCOUNT, null, Caller.ANONYMOUS, expectedRevision = 1) }
            .exceptionOrNull() as CoSkyException
        stale.reason.assert().isEqualTo(ErrorReason.ERROR_REASON_REVISION_CONFLICT)
        stale.detail.revisionConflict.currentRevision.assert().isEqualTo(created.revision)

        val revoked = tokens.revoke(ACCOUNT, created.token.id, Caller.ANONYMOUS, expectedRevision = created.revision)
        revoked.deleted.assert().isTrue()
        revoked.revision.assert().isGreaterThan(created.revision)
    }

    @Test
    fun `revoking a token the account does not hold changes nothing, and says so`() = runBlocking<Unit> {
        val tokens = TokenService(store, caches)
        val before = checkNotNull(store.position(NamingRules.SYSTEM_NAMESPACE)).revision
        val revoked = tokens.revoke(ACCOUNT, "unknown", Caller.ANONYMOUS)
        revoked.deleted.assert().isFalse()
        revoked.revision.assert().isEqualTo(before)
        checkNotNull(store.position(NamingRules.SYSTEM_NAMESPACE)).revision.assert().isEqualTo(before)

        val stale = runCatching { tokens.revoke(ACCOUNT, "unknown", Caller.ANONYMOUS, expectedRevision = before + 1) }
            .exceptionOrNull() as CoSkyException
        stale.reason.assert().isEqualTo(ErrorReason.ERROR_REASON_REVISION_CONFLICT)
    }

    @Test
    fun `an expected revision the account loses to a write in between is a conflict, not tried again`() =
        runBlocking<Unit> {
            val revision = checkNotNull(store.position(NamingRules.SYSTEM_NAMESPACE)).revision
            val failure = runCatching {
                TokenService(racing(listOf(changed(1))), caches)
                    .create(ACCOUNT, null, Caller.ANONYMOUS, expectedRevision = revision)
            }.exceptionOrNull() as CoSkyException
            failure.reason.assert().isEqualTo(ErrorReason.ERROR_REASON_REVISION_CONFLICT)
            failure.detail.revisionConflict.expectedRevision.assert().isEqualTo(revision)
        }

    @Test
    fun `an attempt that is refused leaves an audit line too`() = runBlocking<Unit> {
        val denied = object : Authorizer {
            override val changes: Flow<Unit> = emptyFlow()

            override fun isValid(principal: Principal): Boolean = true

            override fun permission(principal: Principal, verb: Verb, kind: Kind, namespace: String): Permission =
                Permission.None

            override suspend fun requireHeld(principal: Principal, grant: Grant) = Unit
        }
        val tokens = TokenService(store, caches, denied)
        runCatching { tokens.create(ACCOUNT, null, Caller.ANONYMOUS) }.exceptionOrNull()
            .assert().isInstanceOf(CoSkyException::class.java)
        lines.list.single().formattedMessage.assert().contains("\"result\":\"PERMISSION_DENIED\"")
    }

    @Test
    fun `a token for an account written a moment ago finds it, before this replica has seen the write`() =
        runBlocking<Unit> {
            val held = MutableStateFlow(false)
            val lagging = object : Store by store {
                override fun changes(namespace: String, from: Position): Flow<ChangeBatch> =
                    store.changes(namespace, from).onEach { held.first { !it } }
            }
            val caches = WatchCache(lagging, 16, scope)
            caches.latestViewAt(NamingRules.SYSTEM_NAMESPACE, 3.seconds)
            held.value = true
            store.apply(NamingRules.SYSTEM_NAMESPACE, listOf(Mutation.Put(accountNamed("b"))), "test")

            val created = async { TokenService(lagging, caches).create("b", null, Caller.ANONYMOUS) }
            delay(100.milliseconds)
            held.value = false

            created.await().token.id.assert().isNotEmpty()
        }

    @Test
    fun `a token for an account there is not is refused as not found`() = runBlocking<Unit> {
        val error = runCatching { TokenService(store, caches).create("nobody", null, Caller.ANONYMOUS) }
            .exceptionOrNull()

        (error as CoSkyException).reason.assert().isEqualTo(ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND)
    }

    /** A store where someone else writes, just before each of the token's writes, the next of [others]. */
    private fun racing(others: List<Mutation>): Store = object : Store by store {
        private val left = ArrayDeque(others)

        override suspend fun apply(namespace: String, mutations: List<Mutation>, actor: String): ApplyResult {
            left.removeFirstOrNull()?.let { store.apply(namespace, listOf(it), "other") }
            return store.apply(namespace, mutations, actor)
        }
    }

    private fun changed(times: Int): Mutation = Mutation.Put(
        accountNamed(ACCOUNT).toBuilder().apply { metaBuilder.putLabels("changed", "$times") }.build(),
    )

    private val deleted: Mutation = Mutation.Delete(
        ResourceKey.newBuilder()
            .setNamespace(NamingRules.SYSTEM_NAMESPACE)
            .setKind(Kind.KIND_SERVICE_ACCOUNT)
            .setName(ACCOUNT)
            .build(),
    )

    private suspend fun reasonOf(tokens: TokenService): ErrorReason? =
        (runCatching { tokens.create(ACCOUNT, null, Caller.ANONYMOUS) }.exceptionOrNull() as? CoSkyException)?.reason

    @Test
    fun `a token whose account changed meanwhile is written again, on the account as it is now`() =
        runBlocking<Unit> {
            val created = TokenService(racing(listOf(changed(1))), caches).create(ACCOUNT, null, Caller.ANONYMOUS).token

            val account = caches.latestViewAt(NamingRules.SYSTEM_NAMESPACE, 3.seconds).objects.values
                .single { it.hasServiceAccount() && it.meta.name == ACCOUNT }
            account.serviceAccount.tokensList.map { it.id }.assert().containsExactly(created.id)
        }

    @Test
    fun `a token whose account changes at every attempt ends in a revision conflict`() = runBlocking<Unit> {
        reasonOf(TokenService(racing(List(3) { changed(it) }), caches))
            .assert().isEqualTo(ErrorReason.ERROR_REASON_REVISION_CONFLICT)
    }

    @Test
    fun `a token whose account is deleted meanwhile is refused as not found`() = runBlocking<Unit> {
        reasonOf(TokenService(racing(listOf(deleted)), caches))
            .assert().isEqualTo(ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND)
    }

    @Test
    fun `a token whose account is deleted at the last attempt ends in a revision conflict`() = runBlocking<Unit> {
        reasonOf(TokenService(racing(listOf(changed(1), changed(2), deleted)), caches))
            .assert().isEqualTo(ErrorReason.ERROR_REASON_REVISION_CONFLICT)
    }

    @Test
    fun `a token the store refuses for its namespace is refused as not found`() = runBlocking<Unit> {
        val closed = object : Store by store {
            override suspend fun apply(namespace: String, mutations: List<Mutation>, actor: String): ApplyResult =
                ApplyResult.NamespaceNotActive
        }
        reasonOf(TokenService(closed, caches)).assert().isEqualTo(ErrorReason.ERROR_REASON_NAMESPACE_NOT_FOUND)
    }

    private companion object {
        const val ACCOUNT = "a"
    }
}
