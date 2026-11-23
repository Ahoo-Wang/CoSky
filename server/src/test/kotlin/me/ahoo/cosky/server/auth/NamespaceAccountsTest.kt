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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.DeleteRequest
import me.ahoo.cosky.api.v1.ErrorReason
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.PutRequest
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.Subject
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.api.v1.namespaceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.v1.roleBindingSpec
import me.ahoo.cosky.api.v1.serviceAccountSpec
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.resource.ResourceService
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.ApplyResult
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.StoreUnavailableException
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/**
 * The account each namespace comes with (design D45).
 */
class NamespaceAccountsTest {
    private val store = MemoryStore()
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)
    private val caches = WatchCache(store, WINDOW, scope)
    private val accounts = NamespaceAccounts(store, caches)
    private val resources = ResourceService(store, caches, accounts = accounts)
    private val tokens = TokenService(store, caches)
    private val access = AccessControl(caches)

    @AfterEach
    fun stop() {
        scope.cancel()
    }

    private fun scenario(block: suspend CoroutineScope.() -> Unit): Unit = runBlocking(VirtualThreads.dispatcher) {
        withTimeout(20.seconds) {
            store.createNamespace(NamingRules.SYSTEM_NAMESPACE)
            block()
        }
    }

    private fun systemKey(kind: Kind, name: String): ResourceKey = ResourceKey.newBuilder()
        .setNamespace(NamingRules.SYSTEM_NAMESPACE)
        .setKind(kind)
        .setName(name)
        .build()

    private fun namespaceOf(name: String): Resource = resource {
        meta = objectMeta {
            namespace = NamingRules.SYSTEM_NAMESPACE
            this.name = name
        }
        namespace = namespaceSpec {}
    }

    private suspend fun create(name: String) {
        resources.put(PutRequest.newBuilder().setResource(namespaceOf(name)).build(), Caller.ANONYMOUS)
    }

    private suspend fun delete(name: String) {
        val key = systemKey(Kind.KIND_NAMESPACE, name)
        resources.delete(DeleteRequest.newBuilder().setKey(key).build(), Caller.ANONYMOUS)
    }

    private suspend fun stored(kind: Kind, name: String): Resource? =
        caches.latestViewAt(NamingRules.SYSTEM_NAMESPACE, 3.seconds).objects[systemKey(kind, name)]

    private suspend fun account(name: String) = stored(Kind.KIND_SERVICE_ACCOUNT, name)

    private suspend fun binding(name: String) = stored(Kind.KIND_ROLE_BINDING, name)

    private suspend fun tokenOf(account: String): String =
        tokens.create(account, null, Caller.ANONYMOUS).token.plaintext

    /** What [token] may do, as of the latest write. */
    private suspend fun permission(token: String, verb: Verb, kind: Kind, namespace: String): Permission {
        caches.latestViewAt(NamingRules.SYSTEM_NAMESPACE, 3.seconds)
        return access.permission(access.authenticate(token), verb, kind, namespace)
    }

    @Test
    fun `the labels are names the validator takes`() {
        listOf(NamespaceAccounts.NAMESPACE_LABEL, NamespaceAccounts.GENERATION_LABEL).forEach {
            NamingRules.ENTRY_KEY.matches(it).assert().isTrue()
        }
    }

    @Test
    fun `a namespace comes with an account its applications can start with`() = scenario {
        create("dev")
        create("other")

        val generation = stored(Kind.KIND_NAMESPACE, "dev")!!.meta.createRevision
        NamespaceAccounts.generationOf(account("dev")!!).assert().isEqualTo(generation)
        val grant = binding("dev")!!.roleBinding
        grant.role.assert().isEqualTo("client")
        grant.namespacesList.assert().containsExactly("dev")
        val token = tokenOf("dev")
        permission(token, Verb.VERB_GET, Kind.KIND_CONFIG, "dev").assert().isEqualTo(Permission.All)
        permission(token, Verb.VERB_PUT, Kind.KIND_INSTANCE, "dev").assert().isEqualTo(Permission.All)
        permission(token, Verb.VERB_PUT, Kind.KIND_CONFIG, "dev").assert().isEqualTo(Permission.None)
        permission(token, Verb.VERB_GET, Kind.KIND_CONFIG, "other").assert().isEqualTo(Permission.None)
    }

    @Test
    fun `an account of the same name that is someone else's is left alone`() = scenario {
        val theirs = resource {
            meta = objectMeta {
                namespace = NamingRules.SYSTEM_NAMESPACE
                name = "dev"
            }
            serviceAccount = serviceAccountSpec { description = "Someone's own" }
        }
        store.apply(NamingRules.SYSTEM_NAMESPACE, listOf(Mutation.Put(theirs)), "test")

        create("dev")
        accounts.reconcile()

        account("dev")!!.serviceAccount.description.assert().isEqualTo("Someone's own")
        NamespaceAccounts.isManaged(account("dev")!!).assert().isFalse()
        binding("dev").assert().isNull()
    }

    @Test
    fun `deleting a namespace takes its account along`() = scenario {
        create("dev")

        delete("dev")

        account("dev").assert().isNull()
        binding("dev").assert().isNull()
    }

    @Test
    fun `a namespace created again comes with a new account, and the old token reaches nothing`() = scenario {
        create("dev")
        val before = tokenOf("dev")
        delete("dev")

        create("dev")

        val generation = stored(Kind.KIND_NAMESPACE, "dev")!!.meta.createRevision
        NamespaceAccounts.generationOf(account("dev")!!).assert().isEqualTo(generation)
        account("dev")!!.serviceAccount.tokensList.assert().isEmpty()
        val refused = runCatching { permission(before, Verb.VERB_GET, Kind.KIND_CONFIG, "dev") }
        (refused.exceptionOrNull() as CoSkyException).reason.assert()
            .isEqualTo(ErrorReason.ERROR_REASON_UNAUTHENTICATED)
    }

    @Test
    fun `the reconciler gives every namespace its account, and removes those left behind`() = scenario {
        // As the first start writes default, and as namespaces from before this came about.
        store.createNamespace("legacy")
        store.apply(NamingRules.SYSTEM_NAMESPACE, listOf(Mutation.Put(namespaceOf("legacy"))), "test")
        val leftBehind = resource {
            meta = objectMeta {
                namespace = NamingRules.SYSTEM_NAMESPACE
                name = "gone"
                labels.put(NamespaceAccounts.NAMESPACE_LABEL, "gone")
                labels.put(NamespaceAccounts.GENERATION_LABEL, "1")
            }
            roleBinding = roleBindingSpec {
                role = "client"
                subjects += Subject.newBuilder().setType(Subject.Type.TYPE_SERVICE_ACCOUNT).setName("gone").build()
                namespaces += "gone"
            }
        }
        store.apply(NamingRules.SYSTEM_NAMESPACE, listOf(Mutation.Put(leftBehind)), "test")
        account("legacy").assert().isNull()

        accounts.reconcile()

        NamespaceAccounts.isManaged(account("legacy")!!).assert().isTrue()
        NamespaceAccounts.isManaged(binding("legacy")!!).assert().isTrue()
        binding("gone").assert().isNull()
    }

    @Test
    fun `a namespace written while its account cannot be is written all the same, and the reconciler adds it`() =
        scenario {
            val failing = AtomicBoolean(true)
            val flaky = object : Store by store {
                override suspend fun apply(namespace: String, mutations: List<Mutation>, actor: String): ApplyResult {
                    if (failing.get() && mutations.any { it is Mutation.Put && it.resource.hasServiceAccount() }) {
                        throw StoreUnavailableException("Out of reach for the account.")
                    }
                    return store.apply(namespace, mutations, actor)
                }
            }
            val accountsOnFlaky = NamespaceAccounts(flaky, caches)
            val service = ResourceService(flaky, caches, accounts = accountsOnFlaky)

            service.put(PutRequest.newBuilder().setResource(namespaceOf("dev")).build(), Caller.ANONYMOUS)

            stored(Kind.KIND_NAMESPACE, "dev").assert().isNotNull()
            account("dev").assert().isNull()
            failing.set(false)
            accountsOnFlaky.reconcile()
            NamespaceAccounts.isManaged(account("dev")!!).assert().isTrue()
        }

    @Test
    fun `removing the account of a namespace that is still there leaves it`() = scenario {
        create("dev")

        accounts.remove("dev")

        NamespaceAccounts.isManaged(account("dev")!!).assert().isTrue()
        binding("dev").assert().isNotNull()
    }

    @Test
    fun `switched off, a namespace comes with no account`() = scenario {
        val off = NamespaceAccounts(store, caches, enabled = false)
        val service = ResourceService(store, caches, accounts = off)

        service.put(PutRequest.newBuilder().setResource(namespaceOf("dev")).build(), Caller.ANONYMOUS)
        off.reconcile()

        account("dev").assert().isNull()
        binding("dev").assert().isNull()
    }

    @Test
    fun `the first start makes its admin, though namespaces have bindings of their own`() = scenario {
        create("default")
        binding("default").assert().isNotNull()

        AuthBootstrap(store).run(Tokens.generate().plaintext)

        account(AccessModel.ADMIN_ROLE).assert().isNotNull()
    }

    private companion object {
        const val WINDOW = 64
    }
}
