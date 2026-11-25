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

import kotlinx.coroutines.CancellationException
import me.ahoo.cosky.api.kind
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.Subject
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.v1.roleBindingSpec
import me.ahoo.cosky.api.v1.serviceAccountSpec
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.watch.NamespaceView
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.ApplyResult
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.key
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The ServiceAccount and RoleBinding each namespace comes with, of its own name (design D45): the applications of one
 * namespace share the account's token, and need nothing else to start. The binding grants the built-in `client` role in
 * that namespace alone.
 *
 * Both carry the namespace's name and its generation, the `create_revision` of its Namespace object, and the binding
 * counts only for that generation ([AccessModel]). An account or binding of the same name that does not carry them is
 * someone else's, and left alone.
 *
 * @param enabled off, nothing is made or cleaned up; what was made goes on as any other account.
 */
class NamespaceAccounts(
    private val store: Store,
    private val caches: WatchCache,
    private val enabled: Boolean = true,
    private val minRevisionTimeout: Duration = 3.seconds,
) {
    /**
     * Gives [namespace], a Namespace object as stored, its account, unless it has the one of its generation already.
     * An account of an earlier generation is replaced, and its tokens go with it.
     */
    suspend fun ensure(namespace: Resource) {
        if (enabled) {
            ensure(namespace, latest())
        }
    }

    /**
     * Removes the account of the namespace [name], whatever generation it was for, once the namespace is deleted.
     */
    suspend fun remove(name: String) {
        if (!enabled) {
            return
        }
        val view = latest()
        if (view.objects[namespaceKey(name)] == null) {
            delete(listOfNotNull(view.objects[accountKey(name)], view.objects[bindingKey(name)]).filter(::isManaged))
        }
    }

    /**
     * Every namespace with the account of its generation, and no account left of a namespace that is gone, as after a
     * creation or a deletion that stopped half way, or a start with namespaces from before (design D45).
     */
    suspend fun reconcile() {
        if (!enabled) {
            return
        }
        val view = latest()
        val namespaces = view.objects.values.filter { it.kind == Kind.KIND_NAMESPACE }
        namespaces.filter { !it.namespace.terminating }.forEach { ensure(it, view) }
        val names = namespaces.mapTo(mutableSetOf()) { it.meta.name }
        // Only what [ensure] makes: anyone who may write an object here may also give it the labels, and a Namespace
        // or Role carrying them is not to be deleted behind the back of the namespace lifecycle (design D13, D39).
        delete(view.objects.values.filter { isAccountOrBinding(it) && namespaceOf(it) !in names })
    }

    private fun isAccountOrBinding(resource: Resource): Boolean = isManaged(resource) &&
        resource.kind in MANAGED_KINDS &&
        resource.meta.name == namespaceOf(resource)

    private suspend fun ensure(namespace: Resource, view: NamespaceView) {
        val name = namespace.meta.name
        if (name == NamingRules.SYSTEM_NAMESPACE) {
            return
        }
        val generation = namespace.meta.createRevision
        val account = view.objects[accountKey(name)]
        val binding = view.objects[bindingKey(name)]
        listOfNotNull(account, binding).firstOrNull { !isManaged(it) }?.let {
            log.warn(
                "{} {} is not the server's, so namespace {} has no account of its own.",
                it.kind.name.removePrefix("KIND_"),
                name,
                name,
            )
            return
        }
        val mutations = listOfNotNull(
            Mutation.Put(accountOf(name, generation), account?.meta?.revision ?: Mutation.MUST_NOT_EXIST)
                .takeIf { account == null || generationOf(account) != generation },
            Mutation.Put(bindingOf(name, generation), binding?.meta?.revision ?: Mutation.MUST_NOT_EXIST)
                .takeIf { binding == null || generationOf(binding) != generation },
        )
        if (mutations.isEmpty()) {
            return
        }
        when (val result = store.apply(NamingRules.SYSTEM_NAMESPACE, mutations, ACTOR)) {
            is ApplyResult.Applied -> log.info("Namespace {} has an account of its own.", name)
            // Another replica, or a request, got there first; the next reconciliation looks again.
            is ApplyResult.Conflict -> log.debug("The account of namespace {} changed meanwhile.", name)
            else -> log.warn("Could not give namespace {} an account of its own: {}", name, result)
        }
    }

    private suspend fun delete(objects: List<Resource>) {
        objects.forEach { resource ->
            // One at a time, by revision: an account made again meanwhile is not this one.
            store.apply(
                NamingRules.SYSTEM_NAMESPACE,
                listOf(Mutation.Delete(resource.key, resource.meta.revision)),
                ACTOR,
            )
        }
    }

    private suspend fun latest(): NamespaceView = caches.latestViewAt(NamingRules.SYSTEM_NAMESPACE, minRevisionTimeout)

    /**
     * Gives [namespace] its account if it can, and leaves it to the next reconciliation if not: a request that wrote
     * the Namespace object does not fail over its account.
     */
    suspend fun tryEnsure(namespace: Resource) = attempt("give", namespace.meta.name) { ensure(namespace) }

    /** As [tryEnsure], for [remove]. */
    suspend fun tryRemove(name: String) = attempt("remove", name) { remove(name) }

    private suspend fun attempt(what: String, name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            log.warn("Could not {} the account of namespace {} yet; the reconciler will.", what, name, e)
        }
    }

    companion object {
        const val NAMESPACE_LABEL = "cosky.namespace"
        const val GENERATION_LABEL = "cosky.generation"
        private const val ACTOR = "system"
        private val MANAGED_KINDS = setOf(Kind.KIND_SERVICE_ACCOUNT, Kind.KIND_ROLE_BINDING)
        private val log = LoggerFactory.getLogger(NamespaceAccounts::class.java)

        /** Whether the server made [resource] as a namespace's own account or binding. */
        fun isManaged(resource: Resource): Boolean = namespaceOf(resource) != null && generationOf(resource) != null

        fun namespaceOf(resource: Resource): String? = resource.meta.labelsMap[NAMESPACE_LABEL]

        fun generationOf(resource: Resource): Long? = resource.meta.labelsMap[GENERATION_LABEL]?.toLongOrNull()

        private fun accountKey(name: String) = keyOf(Kind.KIND_SERVICE_ACCOUNT, name)

        private fun bindingKey(name: String) = keyOf(Kind.KIND_ROLE_BINDING, name)

        private fun namespaceKey(name: String) = keyOf(Kind.KIND_NAMESPACE, name)

        private fun keyOf(kind: Kind, name: String): ResourceKey = ResourceKey.newBuilder()
            .setNamespace(NamingRules.SYSTEM_NAMESPACE)
            .setKind(kind)
            .setName(name)
            .build()

        private fun accountOf(name: String, generation: Long) = resource {
            meta = metaOf(name, generation)
            serviceAccount = serviceAccountSpec { description = "The applications of namespace $name" }
        }

        private fun bindingOf(name: String, generation: Long) = resource {
            meta = metaOf(name, generation)
            roleBinding = roleBindingSpec {
                role = AccessModel.CLIENT_ROLE
                subjects += Subject.newBuilder().setType(Subject.Type.TYPE_SERVICE_ACCOUNT).setName(name).build()
                namespaces += name
            }
        }

        private fun metaOf(name: String, generation: Long) = objectMeta {
            namespace = NamingRules.SYSTEM_NAMESPACE
            this.name = name
            labels.put(NAMESPACE_LABEL, name)
            labels.put(GENERATION_LABEL, generation.toString())
        }
    }
}
