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

import me.ahoo.cosky.api.isGlobal
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.RoleBindingSpec
import me.ahoo.cosky.api.v1.Rule
import me.ahoo.cosky.api.v1.Subject
import me.ahoo.cosky.api.v1.TokenRef
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.api.validation.NamingRules
import java.util.concurrent.ConcurrentHashMap

/**
 * The ServiceAccounts, Roles and RoleBindings of one revision of `_system`, indexed so that a request costs a few map
 * lookups (design §6.3). Immutable apart from a cache of what it derives.
 */
internal class AccessModel(objects: Collection<Resource>) {
    private val tokens: Map<String, Pair<String, TokenRef>> = objects.filter { it.hasServiceAccount() }
        .flatMap { account -> account.serviceAccount.tokensList.map { it.id to (account.meta.name to it) } }
        .toMap()

    private val roles: Map<String, List<Rule>> = objects.filter { it.hasRole() }
        .associate { it.meta.name to it.role.rulesList } + BUILT_IN_ROLES

    /** The `create_revision` of each Namespace object: which generation a namespace's own account is for. */
    private val generations: Map<String, Long> = objects.filter { it.hasNamespace() }
        .associate { it.meta.name to it.meta.createRevision }

    /**
     * A namespace's own binding counts only for the generation of the namespace it was made for (design D45): one
     * left behind by a namespace deleted and created again grants nothing, however late it is cleaned up.
     */
    private val bindings = objects.filter { it.hasRoleBinding() && isCurrent(it) }.map { it.roleBinding }

    private val rulesByPrincipal = ConcurrentHashMap<Principal, List<EffectiveRule>>()

    /**
     * Whether anyone has been granted anything, the bindings the server makes for namespaces aside; if not, the first
     * start still has to bootstrap.
     */
    val hasBindings: Boolean = objects.any { it.hasRoleBinding() && !NamespaceAccounts.isManaged(it) }

    private fun isCurrent(binding: Resource): Boolean {
        val generation = NamespaceAccounts.generationOf(binding) ?: return !NamespaceAccounts.isManaged(binding)
        return generations[NamespaceAccounts.namespaceOf(binding)] == generation
    }

    /**
     * @return the account and the token, by the id in the token's plaintext.
     */
    fun token(id: String): Pair<String, TokenRef>? = tokens[id]

    fun permission(principal: Principal, verb: Verb, kind: Kind, namespace: String): Permission {
        val matching = effectiveRulesOf(principal).filter { it.grants(verb, kind, namespace) }
        return when {
            matching.isEmpty() -> Permission.None
            matching.any { it.names.isEmpty() } -> Permission.All
            else -> Permission.Names(matching.flatMapTo(mutableSetOf()) { it.names })
        }
    }

    /**
     * Every rule bound to [principal], each limited to where it applies.
     */
    fun rulesOf(principal: Principal): List<Rule> = effectiveRulesOf(principal).map { it.toRule() }

    // By what the rules go by: not when a person's token expires, or every token renewed would add one more.
    private fun effectiveRulesOf(principal: Principal): List<EffectiveRule> = rulesByPrincipal.computeIfAbsent(
        principal.copy(expiresAt = null)
    ) {
        bindings.filter { binding -> binding.subjectsList.any { matches(it, principal) } }
            .flatMap { binding ->
                rulesOf(binding.role, principal).map { EffectiveRule(it, binding.namespacesList.toSet()) }
            }
    }

    /**
     * `service` is the one role whose rules depend on who holds it: Instances only of the service its own name names.
     */
    private fun rulesOf(role: String, principal: Principal): List<Rule> = when (role) {
        SERVICE_ROLE -> serviceRules(principal.name)
        else -> roles[role].orEmpty()
    }

    /**
     * Why [principal] may not make [grant], or null where it holds all of it already: nobody gives more than they have,
     * as in the escalate and bind rules of Kubernetes (design §7.8).
     */
    fun escalationOf(principal: Principal, grant: Grant): String? = when (grant) {
        is Grant.OfRole -> unheld(principal, grant.rules)?.let {
            "${principal.id} may not grant $it, which it does not hold itself."
        }

        is Grant.OfBinding -> escalationOf(principal, grant.binding)

        is Grant.OfAccount -> {
            val account = Principal(Subject.Type.TYPE_SERVICE_ACCOUNT, grant.account)
            unheld(principal, rulesOf(account))?.let {
                "${principal.id} may not create a token of ${account.id}, which may $it: " +
                    "${principal.id} does not hold that itself."
            }
        }
    }

    /**
     * A role that does not exist yet grants whatever it will hold, which a binding limits to its namespaces alone: so
     * it takes holding everything there, as `admin` does. `service` grants each subject its own service, and the
     * members of a group whatever their names are.
     */
    private fun escalationOf(principal: Principal, binding: RoleBindingSpec): String? {
        val role = binding.role
        val known = role == SERVICE_ROLE || role in roles
        val rules = when (role) {
            SERVICE_ROLE -> binding.subjectsList.flatMap { subject ->
                serviceRules(subject.name.takeIf { subject.isNamed })
            }

            else -> roles[role] ?: BUILT_IN_ROLES.getValue(ADMIN_ROLE)
        }
        val boundTo = binding.namespacesList.toSet()
        val unheld = unheld(principal, rules.distinct().map { EffectiveRule(it, boundTo).toRule() }) ?: return null
        return if (known) {
            "${principal.id} may not bind role $role, which grants $unheld: it does not hold that itself."
        } else {
            "Role $role does not exist, so binding it takes holding everything where it is bound, as admin does: " +
                "${principal.id} does not hold $unheld."
        }
    }

    /**
     * The first of what [rules] grant that [principal] does not hold; null if it holds all of it.
     */
    private fun unheld(principal: Principal, rules: List<Rule>): Access? = rules.asSequence()
        .flatMap(::accessesOf)
        .firstNotNullOfOrNull { access ->
            val permission = permission(principal, access.verb, access.kind, access.namespace)
            when {
                access.names.isEmpty() -> access.takeIf { permission != Permission.All }
                else -> access.names.filterNot(permission::allows).takeIf { it.isNotEmpty() }
                    ?.let { access.copy(names = it) }
            }
        }

    private fun accessesOf(rule: Rule): Sequence<Access> = rule.kindsList.asSequence().flatMap { kind ->
        scopesOf(kind, rule.namespacesList).flatMap { namespace ->
            rule.verbsList.map { verb -> Access(verb, kind, namespace, rule.namesList) }
        }
    }

    /**
     * One verb on one kind in one namespace, for [names] or, with none, for every name.
     */
    private data class Access(val verb: Verb, val kind: Kind, val namespace: String, val names: List<String>) {
        override fun toString(): String {
            val where = if (namespace == Permission.WILDCARD) "every namespace" else "namespace $namespace"
            val which = if (names.isEmpty()) "" else " for ${names.joinToString()}"
            return "${verb.name.removePrefix("VERB_")} ${kind.name.removePrefix("KIND_")} in $where$which"
        }
    }

    private fun matches(subject: Subject, principal: Principal): Boolean = when (subject.type) {
        Subject.Type.TYPE_GROUP -> subject.name in principal.groups
        else -> subject.type == principal.type && subject.name == principal.name
    }

    /**
     * A rule as bound: it applies where its own namespaces and those of the binding intersect (design D24).
     */
    private class EffectiveRule(private val rule: Rule, private val boundTo: Set<String>) {
        private val kinds = rule.kindsList.toSet()
        private val verbs = rule.verbsList.toSet()
        private val namespaces = rule.namespacesList.toSet()
        val names: List<String> = rule.namesList

        fun grants(verb: Verb, kind: Kind, namespace: String): Boolean =
            verb in verbs && kind in kinds && covers(namespaces, namespace) && covers(boundTo, namespace)

        fun toRule(): Rule = rule.toBuilder()
            .clearNamespaces()
            .addAllNamespaces(
                when {
                    Permission.WILDCARD in boundTo -> namespaces
                    Permission.WILDCARD in namespaces -> boundTo
                    else -> namespaces intersect boundTo
                },
            )
            .build()

        private fun covers(allowed: Set<String>, namespace: String): Boolean =
            Permission.WILDCARD in allowed || namespace in allowed
    }

    companion object {
        private val READ = listOf(Verb.VERB_GET, Verb.VERB_WATCH)
        private val WRITE = listOf(Verb.VERB_PUT, Verb.VERB_DELETE)
        private val ALL_KINDS = Kind.entries.filter { it != Kind.KIND_UNSPECIFIED && it != Kind.UNRECOGNIZED }

        private fun rule(kinds: List<Kind>, verbs: List<Verb>): Rule = Rule.newBuilder()
            .addAllKinds(kinds)
            .addNamespaces(Permission.WILDCARD)
            .addAllVerbs(verbs)
            .build()

        /**
         * @param service whose Instances it writes; null for any, as for the members of a group, whose names a binding
         * does not tell.
         */
        private fun serviceRules(service: String?): List<Rule> = listOf(
            rule(listOf(Kind.KIND_CONFIG), READ),
            rule(listOf(Kind.KIND_INSTANCE), READ),
            rule(listOf(Kind.KIND_INSTANCE), WRITE).toBuilder().apply { service?.let { addNames(it) } }.build(),
        )

        /** A group stands for members of other names. */
        private val Subject.isNamed: Boolean
            get() = type != Subject.Type.TYPE_GROUP

        /**
         * Where [namespaces] give anything on [kind]: the global kinds live in `_system` alone, and the others never
         * there, so the rest grants nothing to hold. A star means every namespace, which only a star holds.
         */
        private fun scopesOf(kind: Kind, namespaces: List<String>): Sequence<String> {
            val any = Permission.WILDCARD in namespaces
            return when {
                kind.isGlobal -> sequenceOf(NamingRules.SYSTEM_NAMESPACE)
                    .filter { any || it in namespaces }

                any -> sequenceOf(Permission.WILDCARD)
                else -> namespaces.asSequence().filter { it != NamingRules.SYSTEM_NAMESPACE }.distinct()
            }
        }

        /**
         * They hold in every namespace, and a RoleBinding narrows that (design §6.3). A Role of the same name does not
         * replace them. `service` is built in too, but its rules are those of whoever holds it, see [rulesOf].
         */
        private val BUILT_IN_ROLES: Map<String, List<Rule>> = mapOf(
            "admin" to listOf(rule(ALL_KINDS, READ + WRITE)),
            "viewer" to listOf(rule(ALL_KINDS, READ)),
            CLIENT_ROLE to listOf(
                rule(listOf(Kind.KIND_CONFIG), READ),
                rule(listOf(Kind.KIND_INSTANCE), READ + WRITE),
            ),
        )

        const val ADMIN_ROLE = "admin"
        const val CLIENT_ROLE = "client"
        const val SERVICE_ROLE = "service"
    }
}
