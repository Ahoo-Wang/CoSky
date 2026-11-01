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
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.v1.roleBindingSpec
import me.ahoo.cosky.api.v1.roleSpec
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

/**
 * The authorization matrix of design §7.8.
 */
class AccessModelTest {
    private val account = Principal(Subject.Type.TYPE_SERVICE_ACCOUNT, "order")

    private fun subject(type: Subject.Type = Subject.Type.TYPE_SERVICE_ACCOUNT, name: String = "order"): Subject =
        Subject.newBuilder().setType(type).setName(name).build()

    private fun binding(
        role: String,
        namespaces: List<String> = listOf("prod"),
        subject: Subject = subject(),
    ): Resource = resource {
        meta = objectMeta { name = "binding-$role" }
        roleBinding = roleBindingSpec {
            this.role = role
            subjects += subject
            this.namespaces += namespaces
        }
    }

    private fun role(name: String, vararg rules: Rule): Resource = resource {
        meta = objectMeta { this.name = name }
        this.role = roleSpec { this.rules += rules.toList() }
    }

    private fun rule(
        kind: Kind = Kind.KIND_CONFIG,
        verb: Verb = Verb.VERB_GET,
        namespace: String = "*",
        names: List<String> = emptyList(),
    ): Rule = Rule.newBuilder().addKinds(kind).addVerbs(verb).addNamespaces(namespace).addAllNames(names).build()

    private fun AccessModel.config(verb: Verb = Verb.VERB_GET, namespace: String = "prod"): Permission =
        permission(account, verb, Kind.KIND_CONFIG, namespace)

    @Test
    fun `the names validation reserves are the roles that are built in`() {
        NamingRules.BUILT_IN_ROLES.forEach { name ->
            AccessModel(listOf(binding(name, subject = subject()))).rulesOf(account).assert().isNotEmpty()
        }
    }

    @Test
    fun `nothing is granted without a binding`() {
        AccessModel(emptyList()).config().assert().isEqualTo(Permission.None)
    }

    @Test
    fun `a binding only counts for its subjects`() {
        val model = AccessModel(listOf(binding("admin", subject = subject(name = "other"))))
        model.config().assert().isEqualTo(Permission.None)
    }

    @Test
    fun `a subject of another type with the same name is someone else`() {
        val model = AccessModel(listOf(binding("admin", subject = subject(Subject.Type.TYPE_USER))))
        model.config().assert().isEqualTo(Permission.None)
    }

    @Test
    fun `a group binding counts for its members`() {
        val model = AccessModel(listOf(binding("admin", subject = subject(Subject.Type.TYPE_GROUP, "devs"))))
        val member = Principal(Subject.Type.TYPE_USER, "ann", setOf("devs"))
        model.permission(member, Verb.VERB_GET, Kind.KIND_CONFIG, "prod").assert().isEqualTo(Permission.All)
        model.permission(account, Verb.VERB_GET, Kind.KIND_CONFIG, "prod").assert().isEqualTo(Permission.None)
    }

    @Test
    fun `a binding limits a role to its namespaces`() {
        val model = AccessModel(listOf(binding("admin", listOf("prod"))))
        model.config(namespace = "prod").assert().isEqualTo(Permission.All)
        model.config(namespace = "test").assert().isEqualTo(Permission.None)
    }

    @Test
    fun `a star in a binding adds no restriction`() {
        val model = AccessModel(listOf(binding("admin", listOf("*"))))
        model.config(namespace = "anything").assert().isEqualTo(Permission.All)
    }

    @Test
    fun `a rule and its binding intersect`() {
        val model = AccessModel(listOf(role("custom", rule(namespace = "test")), binding("custom", listOf("prod"))))
        model.config(namespace = "prod").assert().isEqualTo(Permission.None)
        model.config(namespace = "test").assert().isEqualTo(Permission.None)
    }

    @Test
    fun `a rule grants only its verbs and kinds`() {
        val model = AccessModel(listOf(role("custom", rule()), binding("custom")))
        model.config(Verb.VERB_GET).assert().isEqualTo(Permission.All)
        model.config(Verb.VERB_PUT).assert().isEqualTo(Permission.None)
        model.permission(account, Verb.VERB_GET, Kind.KIND_INSTANCE, "prod").assert().isEqualTo(Permission.None)
    }

    @Test
    fun `names narrow a rule, and any rule without names lifts that`() {
        val narrow = AccessModel(listOf(role("custom", rule(names = listOf("a.yaml", "shared/*"))), binding("custom")))
        val permission = narrow.config()
        permission.allows("a.yaml").assert().isTrue()
        permission.allows("shared/db.yaml").assert().isTrue()
        permission.allows("b.yaml").assert().isFalse()
        permission.allows("a.yaml.bak").assert().isFalse()

        val wide = AccessModel(listOf(role("custom", rule(names = listOf("a")), rule()), binding("custom")))
        wide.config().assert().isEqualTo(Permission.All)
    }

    @Test
    fun `the built-in viewer reads and does not write`() {
        val model = AccessModel(listOf(binding("viewer")))
        model.config(Verb.VERB_GET).assert().isEqualTo(Permission.All)
        model.config(Verb.VERB_WATCH).assert().isEqualTo(Permission.All)
        model.config(Verb.VERB_PUT).assert().isEqualTo(Permission.None)
    }

    @Test
    fun `the built-in client reads configs and writes instances`() {
        val model = AccessModel(listOf(binding("client")))
        model.config(Verb.VERB_WATCH).assert().isEqualTo(Permission.All)
        model.config(Verb.VERB_PUT).assert().isEqualTo(Permission.None)
        model.permission(account, Verb.VERB_PUT, Kind.KIND_INSTANCE, "prod").assert().isEqualTo(Permission.All)
        model.permission(account, Verb.VERB_GET, Kind.KIND_ROLE, "prod").assert().isEqualTo(Permission.None)
    }

    @Test
    fun `a role cannot redefine a built-in one`() {
        val model = AccessModel(listOf(role("viewer", rule(verb = Verb.VERB_PUT)), binding("viewer")))
        model.config(Verb.VERB_PUT).assert().isEqualTo(Permission.None)
    }

    @Test
    fun `a binding to a role that does not exist grants nothing`() {
        AccessModel(listOf(binding("missing"))).config().assert().isEqualTo(Permission.None)
    }

    @Test
    fun `an instance is authorized by its service, anything else by its name`() {
        val instance = resource {
            meta = objectMeta { name = "order-1" }
            this.instance = me.ahoo.cosky.api.v1.instanceSpec { service = "order" }
        }
        instance.authorizedName.assert().isEqualTo("order")
        role("custom").authorizedName.assert().isEqualTo("custom")
    }
}
