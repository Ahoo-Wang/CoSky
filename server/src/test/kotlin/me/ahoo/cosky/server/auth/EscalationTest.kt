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
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.ErrorReason
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.PutRequest
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.Rule
import me.ahoo.cosky.api.v1.Subject
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.v1.roleBindingSpec
import me.ahoo.cosky.api.v1.roleSpec
import me.ahoo.cosky.api.v1.serviceAccountSpec
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.resource.ResourceService
import me.ahoo.cosky.server.watch.WatchCache
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
 * Writing a Role or a RoleBinding, or creating a token, through the services with the real access rules: nobody gives
 * more than they hold (design §7.8).
 */
class EscalationTest {
    private val store = MemoryStore()
    private val held = MutableStateFlow(false)

    /** A replica that stops seeing writes while [held]. */
    private val lagging = object : Store by store {
        override fun changes(namespace: String, from: Position): Flow<ChangeBatch> =
            store.changes(namespace, from).onEach { held.first { !it } }
    }
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)
    private val caches = WatchCache(lagging, 64, scope)
    private val access = AccessControl(caches)
    private val resources = ResourceService(lagging, caches, authorizer = access)
    private val tokens = TokenService(lagging, caches, access)
    private val ops = Caller(Principal(Subject.Type.TYPE_SERVICE_ACCOUNT, "ops"))

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

    /**
     * Writes straight to the store, as an operator before the test, and waits for this replica to see it.
     */
    private suspend fun system(vararg resources: Resource) {
        store.apply(NamingRules.SYSTEM_NAMESPACE, resources.map { Mutation.Put(it) }, "test")
        caches.latestViewAt(NamingRules.SYSTEM_NAMESPACE, 3.seconds)
    }

    private fun meta(name: String) = objectMeta {
        namespace = NamingRules.SYSTEM_NAMESPACE
        this.name = name
    }

    private fun account(name: String): Resource = resource {
        meta = meta(name)
        serviceAccount = serviceAccountSpec {}
    }

    private fun role(name: String, vararg rules: Rule): Resource = resource {
        meta = meta(name)
        role = roleSpec { this.rules += rules.toList() }
    }

    private fun binding(name: String, role: String, subject: String, vararg namespaces: String): Resource = resource {
        meta = meta(name)
        roleBinding = roleBindingSpec {
            this.role = role
            subjects += Subject.newBuilder().setType(Subject.Type.TYPE_SERVICE_ACCOUNT).setName(subject).build()
            this.namespaces += namespaces.toList()
        }
    }

    private fun rule(kind: Kind, vararg verbs: Verb, namespace: String = "*", names: List<String> = emptyList()) =
        Rule.newBuilder().addKinds(kind).addAllVerbs(verbs.toList()).addNamespaces(namespace).addAllNames(names).build()

    /** What lets [ops] write Roles, RoleBindings and ServiceAccounts, and so create tokens: nothing more. */
    private val manager = role(
        "manager",
        rule(Kind.KIND_ROLE, Verb.VERB_PUT, namespace = NamingRules.SYSTEM_NAMESPACE),
        rule(Kind.KIND_ROLE_BINDING, Verb.VERB_PUT, namespace = NamingRules.SYSTEM_NAMESPACE),
        rule(Kind.KIND_SERVICE_ACCOUNT, Verb.VERB_PUT, namespace = NamingRules.SYSTEM_NAMESPACE),
    )

    private suspend fun put(resource: Resource, caller: Caller = ops) =
        resources.put(PutRequest.newBuilder().setResource(resource).build(), caller)

    private suspend fun refused(block: suspend () -> Unit): CoSkyException {
        val failure = runCatching { block() }.exceptionOrNull()
        failure.assert().isInstanceOf(CoSkyException::class.java)
        (failure as CoSkyException).reason.assert().isEqualTo(ErrorReason.ERROR_REASON_PERMISSION_DENIED)
        return failure
    }

    @Test
    fun `a role writer cannot widen a role it holds, and may narrow it`() = scenario {
        val reader = rule(Kind.KIND_CONFIG, Verb.VERB_GET, namespace = "prod")
        system(manager, binding("ops-manager", "manager", "ops", "*"))
        system(role("ops", reader, rule(Kind.KIND_INSTANCE, Verb.VERB_GET)), binding("ops", "ops", "ops", "*"))

        refused { put(role("ops", reader, rule(Kind.KIND_CONFIG, Verb.VERB_PUT, namespace = "prod"))) }
            .message.assert().contains("PUT CONFIG in namespace prod")
        refused { put(role("ops", rule(Kind.KIND_CONFIG, Verb.VERB_GET))) }
            .message.assert().contains("GET CONFIG in every namespace")

        put(role("ops", reader)).changed.assert().isTrue()
    }

    @Test
    fun `a role writer cannot create a role broader than itself, and may create one as narrow`() = scenario {
        system(
            manager,
            binding("ops-manager", "manager", "ops", "*"),
            binding("ops-client", "client", "ops", "dev"),
        )

        put(role("reader", rule(Kind.KIND_CONFIG, Verb.VERB_GET, Verb.VERB_WATCH, namespace = "dev"))).changed
            .assert().isTrue()
        refused { put(role("writer", rule(Kind.KIND_CONFIG, Verb.VERB_PUT, namespace = "dev"))) }
        refused { put(role("elsewhere", rule(Kind.KIND_CONFIG, Verb.VERB_GET, namespace = "prod"))) }
        refused { put(role("binder", rule(Kind.KIND_ROLE_BINDING, Verb.VERB_DELETE))) }
            .message.assert().contains("DELETE ROLE_BINDING in namespace _system")
    }

    @Test
    fun `a binding writer cannot bind admin to itself, or a role where it does not hold it`() = scenario {
        system(
            manager,
            binding("ops-manager", "manager", "ops", "*"),
            binding("ops-client", "client", "ops", "dev"),
        )

        refused { put(binding("ops-admin", "admin", "ops", "*")) }
        refused { put(binding("ops-admin", "admin", "ops", "dev")) }.message.assert().contains("PUT CONFIG")
        refused { put(binding("ops-client-prod", "client", "ops", "prod")) }
            .message.assert().contains("namespace prod")
        refused { put(binding("ops-later", "later", "ops", "dev")) }.message.assert().contains("does not exist")

        put(binding("app-client", "client", "app", "dev")).changed.assert().isTrue()
        put(binding("app-service", "service", "app", "dev")).changed.assert().isTrue()
    }

    @Test
    fun `a token for an account that may do more than its creator is refused`() = scenario {
        system(
            manager,
            binding("ops-manager", "manager", "ops", "*"),
            binding("ops-client", "client", "ops", "dev"),
            account("dev"),
            binding("dev", "client", "dev", "dev"),
            account("prod"),
            binding("prod", "client", "prod", "prod"),
            account("root"),
            binding("root", "admin", "root", "*"),
        )

        tokens.create("dev", null, ops).token.id.assert().isNotEmpty()
        refused { tokens.create("prod", null, ops) }.message.assert()
            .contains("may not create a token of serviceaccount:prod")
        refused { tokens.create("root", null, ops) }
        store.readAll(NamingRules.SYSTEM_NAMESPACE).first { it.meta.name == "root" }
            .serviceAccount.tokensList.assert().isEmpty()
    }

    @Test
    fun `an admin is not held back`() = scenario {
        val admin = Caller(Principal(Subject.Type.TYPE_SERVICE_ACCOUNT, "root"))
        system(account("root"), binding("root", "admin", "root", "*"), account("other"))

        put(role("everything", rule(Kind.KIND_CONFIG, Verb.VERB_PUT, Verb.VERB_DELETE)), admin).changed
            .assert().isTrue()
        put(binding("other-admin", "admin", "other", "*"), admin).changed.assert().isTrue()
        put(binding("other-later", "later", "other", "*"), admin).changed.assert().isTrue()
        tokens.create("other", null, admin).token.id.assert().isNotEmpty()
    }

    @Test
    fun `with authentication off nothing is held back`() = scenario {
        val open = ResourceService(store, caches)

        open.put(PutRequest.newBuilder().setResource(binding("a", "admin", "a", "*")).build(), Caller.ANONYMOUS)
            .changed.assert().isTrue()
    }

    @Test
    fun `a replica that has not seen a permission taken away cannot be used to give it`() = scenario {
        system(
            manager,
            binding("ops-manager", "manager", "ops", "*"),
            binding("ops-admin", "admin", "ops", "*"),
            account("root"),
            binding("root", "admin", "root", "*"),
        )
        held.value = true
        val revoked = binding("ops-admin", "admin", "ops", "*").key
        store.apply(NamingRules.SYSTEM_NAMESPACE, listOf(Mutation.Delete(revoked)), "test")

        val binding = async { runCatching { put(binding("other-admin", "admin", "other", "*")) }.exceptionOrNull() }
        val token = async { runCatching { tokens.create("root", null, ops) }.exceptionOrNull() }
        delay(200.milliseconds)
        held.value = false

        (binding.await() as CoSkyException).reason.assert().isEqualTo(ErrorReason.ERROR_REASON_PERMISSION_DENIED)
        (token.await() as CoSkyException).reason.assert().isEqualTo(ErrorReason.ERROR_REASON_PERMISSION_DENIED)
    }
}
