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
import me.ahoo.cosky.api.v1.RoleBindingSpec
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
 * Nobody gives more than they hold (design §7.8): what a Role, a RoleBinding or a token grants, against its writer.
 */
class AccessModelEscalationTest {
    private val ops = Principal(Subject.Type.TYPE_SERVICE_ACCOUNT, "ops")

    private fun subject(name: String = "ops", type: Subject.Type = Subject.Type.TYPE_SERVICE_ACCOUNT): Subject =
        Subject.newBuilder().setType(type).setName(name).build()

    private fun spec(role: String, namespaces: List<String>, subject: Subject = subject()): RoleBindingSpec =
        roleBindingSpec {
            this.role = role
            subjects += subject
            this.namespaces += namespaces
        }

    private fun binding(role: String, namespaces: List<String>, subject: Subject = subject()): Resource = resource {
        meta = objectMeta { name = "${subject.name}-$role-${namespaces.joinToString("-")}" }
        roleBinding = spec(role, namespaces, subject)
    }

    private fun role(name: String, vararg rules: Rule): Resource = resource {
        meta = objectMeta { this.name = name }
        role = roleSpec { this.rules += rules.toList() }
    }

    private fun rule(
        kinds: List<Kind>,
        verbs: List<Verb>,
        namespaces: List<String> = listOf("*"),
        names: List<String> = emptyList(),
    ): Rule = Rule.newBuilder()
        .addAllKinds(kinds)
        .addAllVerbs(verbs)
        .addAllNamespaces(namespaces)
        .addAllNames(names)
        .build()

    private fun model(vararg objects: Resource) = AccessModel(objects.toList())

    private fun AccessModel.role(vararg rules: Rule): String? = escalationOf(ops, Grant.OfRole(rules.toList()))

    private fun AccessModel.binding(role: String, namespaces: List<String>, subject: Subject = subject("other")) =
        escalationOf(ops, Grant.OfBinding(spec(role, namespaces, subject)))

    private fun AccessModel.tokenOf(account: String): String? = escalationOf(ops, Grant.OfAccount(account))

    @Test
    fun `an admin holds whatever a role, a binding or a token grants`() {
        val model = model(binding("admin", listOf("*")), binding("admin", listOf("*"), subject("root")))

        val kinds = Kind.entries.filter { it != Kind.KIND_UNSPECIFIED && it != Kind.UNRECOGNIZED }
        model.role(rule(kinds, listOf(Verb.VERB_GET, Verb.VERB_PUT, Verb.VERB_DELETE))).assert().isNull()
        model.binding("admin", listOf("*"), subject("ops")).assert().isNull()
        model.binding("not-yet", listOf("*")).assert().isNull()
        model.tokenOf("root").assert().isNull()
    }

    @Test
    fun `a role that grants more than its writer holds is refused, saying what`() {
        val model = model(
            role("ops", rule(listOf(Kind.KIND_CONFIG), listOf(Verb.VERB_GET), listOf("prod"))),
            binding("ops", listOf("*")),
        )

        model.role(rule(listOf(Kind.KIND_CONFIG), listOf(Verb.VERB_GET), listOf("prod"))).assert().isNull()
        model.role(rule(listOf(Kind.KIND_CONFIG), listOf(Verb.VERB_GET, Verb.VERB_PUT), listOf("prod"))).assert()
            .isEqualTo("serviceaccount:ops may not grant PUT CONFIG in namespace prod, which it does not hold itself.")
        model.role(rule(listOf(Kind.KIND_CONFIG, Kind.KIND_INSTANCE), listOf(Verb.VERB_GET), listOf("prod")))
            .assert().contains("GET INSTANCE in namespace prod")
    }

    @Test
    fun `a rule for every namespace takes holding it in every namespace`() {
        val held = rule(listOf(Kind.KIND_CONFIG), listOf(Verb.VERB_GET))
        val inTwo = model(role("reader", held), binding("reader", listOf("prod", "test")))
        val everywhere = model(role("reader", held), binding("reader", listOf("*")))

        inTwo.role(rule(listOf(Kind.KIND_CONFIG), listOf(Verb.VERB_GET), listOf("prod", "test"))).assert().isNull()
        inTwo.role(held).assert().contains("GET CONFIG in every namespace")
        inTwo.role(rule(listOf(Kind.KIND_CONFIG), listOf(Verb.VERB_GET), listOf("prod", "dev"))).assert()
            .contains("in namespace dev")
        everywhere.role(held).assert().isNull()
        everywhere.role(rule(listOf(Kind.KIND_CONFIG), listOf(Verb.VERB_GET), listOf("dev"))).assert().isNull()
    }

    @Test
    fun `names narrow what is granted, and a prefix holds the names and prefixes under it`() {
        val model = model(
            role("order", rule(listOf(Kind.KIND_CONFIG), listOf(Verb.VERB_PUT), names = listOf("order*", "shared"))),
            binding("order", listOf("prod")),
        )
        fun put(vararg names: String) =
            model.role(rule(listOf(Kind.KIND_CONFIG), listOf(Verb.VERB_PUT), listOf("prod"), names.toList()))

        put("order-service.yaml", "order*", "order/a*", "shared").assert().isNull()
        put("o*").assert().contains("PUT CONFIG in namespace prod for o*")
        put("order.yaml", "payment.yaml", "shared/a").assert()
            .contains("for payment.yaml, shared/a")
            .doesNotContain("order.yaml")
        put().assert().contains("PUT CONFIG in namespace prod,")
    }

    @Test
    fun `rules of several bindings add up`() {
        val model = model(
            role("a", rule(listOf(Kind.KIND_CONFIG), listOf(Verb.VERB_PUT), names = listOf("a*"))),
            role("b", rule(listOf(Kind.KIND_CONFIG), listOf(Verb.VERB_PUT), names = listOf("b*"))),
            binding("a", listOf("*")),
            binding("b", listOf("prod")),
        )

        model.role(rule(listOf(Kind.KIND_CONFIG), listOf(Verb.VERB_PUT), listOf("prod"), listOf("a1", "b1")))
            .assert().isNull()
        model.role(rule(listOf(Kind.KIND_CONFIG), listOf(Verb.VERB_PUT), listOf("test"), listOf("a1", "b1")))
            .assert().contains("in namespace test for b1")
    }

    @Test
    fun `a kind grants nothing where it cannot live`() {
        val system = listOf(NamingRules.SYSTEM_NAMESPACE)
        val model = model(
            role("accounts", rule(listOf(Kind.KIND_SERVICE_ACCOUNT), listOf(Verb.VERB_GET), system)),
            binding("accounts", listOf("*")),
        )

        // ServiceAccounts live in _system alone, so every namespace means _system for them.
        model.role(rule(listOf(Kind.KIND_SERVICE_ACCOUNT), listOf(Verb.VERB_GET))).assert().isNull()
        // Configs never live in _system, and Namespaces never in prod: such rules grant nothing to hold.
        model.role(rule(listOf(Kind.KIND_CONFIG), listOf(Verb.VERB_PUT), system)).assert().isNull()
        model.role(rule(listOf(Kind.KIND_NAMESPACE), listOf(Verb.VERB_PUT), listOf("prod"))).assert().isNull()
        model.role(rule(listOf(Kind.KIND_ROLE), listOf(Verb.VERB_GET))).assert()
            .contains("GET ROLE in namespace _system")
    }

    @Test
    fun `a binding grants its role where the binding and each rule meet`() {
        val model = model(
            binding("client", listOf("dev")),
            role("dev-reader", rule(listOf(Kind.KIND_CONFIG), listOf(Verb.VERB_GET), listOf("dev"))),
        )

        model.binding("client", listOf("dev")).assert().isNull()
        model.binding("client", listOf("prod")).assert()
            .isEqualTo(
                "serviceaccount:ops may not bind role client, which grants GET CONFIG in namespace prod: " +
                    "it does not hold that itself.",
            )
        model.binding("client", listOf("*")).assert().contains("in every namespace")
        // The role holds in dev alone, wherever it is bound.
        model.binding("dev-reader", listOf("*")).assert().isNull()
        // Bound in dev, viewer reads no more than client there: what lives in _system is not in dev.
        model.binding("viewer", listOf("dev")).assert().isNull()
        model.binding("viewer", listOf(NamingRules.SYSTEM_NAMESPACE)).assert()
            .contains("GET NAMESPACE in namespace _system")
    }

    @Test
    fun `binding admin takes holding everything where it is bound`() {
        val model = model(binding("viewer", listOf("*")), binding("admin", listOf("dev")))

        model.binding("admin", listOf("dev"), subject("ops")).assert().isNull()
        model.binding("admin", listOf("prod"), subject("ops")).assert().contains("PUT")
        model.binding("viewer", listOf("*")).assert().isNull()
    }

    @Test
    fun `a role that does not exist yet takes holding everything where it is bound`() {
        val model = model(binding("viewer", listOf("*")), binding("admin", listOf("dev")))

        model.binding("later", listOf("dev")).assert().isNull()
        model.binding("later", listOf("prod")).assert()
            .startsWith("Role later does not exist, so binding it takes holding everything where it is bound")
    }

    @Test
    fun `service grants each subject the Instances of its own service`() {
        val order = model(binding("service", listOf("prod")))

        order.binding("service", listOf("prod"), subject("ops")).assert().isNull()
        order.binding("service", listOf("prod"), subject("ops", Subject.Type.TYPE_USER)).assert().isNull()
        order.binding("service", listOf("prod"), subject("payment")).assert()
            .contains("which grants PUT INSTANCE in namespace prod for payment")
        order.binding("service", listOf("test"), subject("ops")).assert().contains("in namespace test")
    }

    @Test
    fun `service bound to a group grants the services of members whatever their names`() {
        val group = subject("devs", Subject.Type.TYPE_GROUP)

        model(binding("service", listOf("prod"))).binding("service", listOf("prod"), group).assert()
            .contains("which grants PUT INSTANCE in namespace prod:")
        model(binding("client", listOf("prod"))).binding("service", listOf("prod"), group).assert().isNull()
    }

    @Test
    fun `the service role of the writer holds its own service alone`() {
        val model = model(binding("service", listOf("prod")))

        model.role(rule(listOf(Kind.KIND_INSTANCE), listOf(Verb.VERB_PUT), listOf("prod"), listOf("ops"))).assert()
            .isNull()
        model.role(rule(listOf(Kind.KIND_INSTANCE), listOf(Verb.VERB_PUT), listOf("prod"))).assert()
            .contains("PUT INSTANCE in namespace prod,")
    }

    @Test
    fun `a token takes holding whatever its account may do`() {
        val model = model(
            binding("client", listOf("prod"), subject("prod")),
            binding("service", listOf("prod"), subject("order")),
            binding("viewer", listOf("*")),
        )

        model.tokenOf("prod").assert()
            .isEqualTo(
                "serviceaccount:ops may not create a token of serviceaccount:prod, which may PUT INSTANCE in " +
                    "namespace prod: serviceaccount:ops does not hold that itself.",
            )
        model.tokenOf("order").assert().contains("PUT INSTANCE in namespace prod for order")
        model.tokenOf("nobody").assert().isNull()
        model(binding("client", listOf("prod")), binding("client", listOf("prod"), subject("prod")))
            .tokenOf("prod").assert().isNull()
    }

    @Test
    fun `a person holds what the groups it is in hold`() {
        val ann = Principal(Subject.Type.TYPE_USER, "ann", groups = setOf("admins"))
        val model = model(binding("admin", listOf("*"), subject("admins", Subject.Type.TYPE_GROUP)))

        model.escalationOf(ann, Grant.OfBinding(spec("admin", listOf("*"), subject("ann", Subject.Type.TYPE_USER))))
            .assert().isNull()
        model.escalationOf(ops, Grant.OfBinding(spec("admin", listOf("*")))).assert().isNotNull()
    }
}
