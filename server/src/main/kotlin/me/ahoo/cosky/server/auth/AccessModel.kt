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

import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.Rule
import me.ahoo.cosky.api.v1.Subject
import me.ahoo.cosky.api.v1.TokenRef
import me.ahoo.cosky.api.v1.Verb
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

    private val bindings = objects.filter { it.hasRoleBinding() }.map { it.roleBinding }

    private val rulesByPrincipal = ConcurrentHashMap<Principal, List<EffectiveRule>>()

    /**
     * Whether anyone has been granted anything; if not, the first start still has to bootstrap.
     */
    val hasBindings: Boolean
        get() = bindings.isNotEmpty()

    /**
     * @return the account and the token, by the id in the token's plaintext.
     */
    fun token(id: String): Pair<String, TokenRef>? = tokens[id]

    fun permission(principal: Principal, verb: Verb, kind: Kind, namespace: String): Permission {
        val matching = rulesOf(principal).filter { it.grants(verb, kind, namespace) }
        return when {
            matching.isEmpty() -> Permission.None
            matching.any { it.names.isEmpty() } -> Permission.All
            else -> Permission.Names(matching.flatMapTo(mutableSetOf()) { it.names })
        }
    }

    private fun rulesOf(principal: Principal): List<EffectiveRule> = rulesByPrincipal.computeIfAbsent(principal) {
        bindings.filter { binding -> binding.subjectsList.any { matches(it, principal) } }
            .flatMap { binding ->
                roles[binding.role].orEmpty().map { EffectiveRule(it, binding.namespacesList.toSet()) }
            }
    }

    private fun matches(subject: Subject, principal: Principal): Boolean = when (subject.type) {
        Subject.Type.TYPE_GROUP -> subject.name in principal.groups
        else -> subject.type == principal.type && subject.name == principal.name
    }

    /**
     * A rule as bound: it applies where its own namespaces and those of the binding intersect (design D24).
     */
    private class EffectiveRule(rule: Rule, private val boundTo: Set<String>) {
        private val kinds = rule.kindsList.toSet()
        private val verbs = rule.verbsList.toSet()
        private val namespaces = rule.namespacesList.toSet()
        val names: List<String> = rule.namesList

        fun grants(verb: Verb, kind: Kind, namespace: String): Boolean =
            verb in verbs && kind in kinds && covers(namespaces, namespace) && covers(boundTo, namespace)

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
         * They hold in every namespace, and a RoleBinding narrows that (design §6.3). A Role of the same name does not
         * replace them.
         */
        private val BUILT_IN_ROLES: Map<String, List<Rule>> = mapOf(
            "admin" to listOf(rule(ALL_KINDS, READ + WRITE)),
            "viewer" to listOf(rule(ALL_KINDS, READ)),
            "client" to listOf(
                rule(listOf(Kind.KIND_CONFIG), READ),
                rule(listOf(Kind.KIND_INSTANCE), READ + WRITE),
            ),
        )

        const val ADMIN_ROLE = "admin"
    }
}
