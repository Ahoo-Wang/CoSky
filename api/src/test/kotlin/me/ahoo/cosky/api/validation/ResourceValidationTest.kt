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
package me.ahoo.cosky.api.validation

import com.google.protobuf.ByteString
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.EndpointKt
import me.ahoo.cosky.api.v1.InstanceSpec
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.ObjectMetaKt
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.RuleKt
import me.ahoo.cosky.api.v1.Subject
import me.ahoo.cosky.api.v1.SubjectKt
import me.ahoo.cosky.api.v1.TokenRef
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.api.v1.ViolationCode
import me.ahoo.cosky.api.v1.copy
import me.ahoo.cosky.api.v1.endpoint
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.v1.rule
import me.ahoo.cosky.api.v1.subject
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ResourceValidationTest {
    private val validator = Validator()

    private fun Resource.violations() = validator.validate(this).codes()

    @Test
    fun `minimal resources of every kind are valid`() {
        Fixtures.all.forEach { it.violations().assert().isEmpty() }
    }

    @Test
    fun `spec is required`() {
        resource { meta = Fixtures.config.meta }.violations()
            .assert().containsExactly("spec" to ViolationCode.VIOLATION_CODE_REQUIRED)
    }

    @Test
    fun `meta is required`() {
        Fixtures.config.toBuilder().clearMeta().build().violations()
            .assert().containsExactly("meta" to ViolationCode.VIOLATION_CODE_REQUIRED)
    }

    @Test
    fun `an empty resource reports both spec and meta`() {
        Resource.getDefaultInstance().violations()
            .assert().containsExactly(
                "spec" to ViolationCode.VIOLATION_CODE_REQUIRED,
                "meta" to ViolationCode.VIOLATION_CODE_REQUIRED,
            )
    }

    @Test
    fun `read-only fields are ignored`() {
        Fixtures.config.copy {
            meta = meta.copy {
                revision = -5
                createRevision = -5
                updatedBy = "anyone"
            }
            config = config.copy { sha256 = "not-a-hash" }
        }.violations().assert().isEmpty()
    }

    // --- meta.namespace ---

    @Test
    fun `namespaced kinds require a namespace`() {
        Fixtures.config.withMeta { namespace = "" }.violations()
            .assert().containsExactly("meta.namespace" to ViolationCode.VIOLATION_CODE_REQUIRED)
    }

    @Test
    fun `namespaced kinds reject the system namespace`() {
        Fixtures.instance.withMeta { namespace = NamingRules.SYSTEM_NAMESPACE }.violations()
            .assert().containsExactly("meta.namespace" to ViolationCode.VIOLATION_CODE_RESERVED_NAME)
    }

    @Test
    fun `namespace longer than 63 characters is too long`() {
        Fixtures.config.withMeta { namespace = "a".repeat(63) }.violations().assert().isEmpty()
        Fixtures.config.withMeta { namespace = "a".repeat(64) }.violations()
            .assert().containsExactly("meta.namespace" to ViolationCode.VIOLATION_CODE_TOO_LONG)
    }

    @ParameterizedTest
    @ValueSource(strings = ["Prod", "-prod", "prod-", "pr_od", "cosky-{default}"])
    fun `namespace must match the rule`(namespace: String) {
        Fixtures.config.withMeta { this.namespace = namespace }.violations()
            .assert().containsExactly("meta.namespace" to ViolationCode.VIOLATION_CODE_INVALID_FORMAT)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "prod", "_System"])
    fun `global kinds live in the system namespace`(namespace: String) {
        listOf(Fixtures.namespace, Fixtures.serviceAccount, Fixtures.role, Fixtures.roleBinding).forEach {
            it.withMeta { this.namespace = namespace }.violations()
                .assert().containsExactly("meta.namespace" to ViolationCode.VIOLATION_CODE_NOT_ALLOWED)
        }
    }

    // --- meta.name ---

    @Test
    fun `name is required for every kind`() {
        Fixtures.all.forEach {
            it.withMeta { name = "" }.violations().assert().containsExactly(
                "meta.name" to ViolationCode.VIOLATION_CODE_REQUIRED,
            )
        }
    }

    @Test
    fun `namespace names follow the namespace rule`() {
        Fixtures.namespace.withMeta { name = "a".repeat(63) }.violations().assert().isEmpty()
        Fixtures.namespace.withMeta { name = "a".repeat(64) }.violations()
            .assert().containsExactly("meta.name" to ViolationCode.VIOLATION_CODE_TOO_LONG)
        Fixtures.namespace.withMeta { name = "Prod" }.violations()
            .assert().containsExactly("meta.name" to ViolationCode.VIOLATION_CODE_INVALID_FORMAT)
        Fixtures.namespace.withMeta { name = NamingRules.SYSTEM_NAMESPACE }.violations()
            .assert().containsExactly("meta.name" to ViolationCode.VIOLATION_CODE_RESERVED_NAME)
    }

    @Test
    fun `config names allow paths up to 253 characters`() {
        Fixtures.config.withMeta { name = "shared/datasource.yaml" }.violations().assert().isEmpty()
        Fixtures.config.withMeta { name = "a".repeat(253) }.violations().assert().isEmpty()
        Fixtures.config.withMeta { name = "a".repeat(254) }.violations()
            .assert().containsExactly("meta.name" to ViolationCode.VIOLATION_CODE_TOO_LONG)
        Fixtures.config.withMeta { name = "a b" }.violations()
            .assert().containsExactly("meta.name" to ViolationCode.VIOLATION_CODE_INVALID_FORMAT)
    }

    @ParameterizedTest
    @ValueSource(strings = ["a/b", "a/b/c.yaml", "a/.b", "a..b/c", ".a", "..."])
    fun `config names allow non-dot segments`(name: String) {
        Fixtures.config.withMeta { this.name = name }.violations().assert().isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = [".", "..", "/a", "a/", "a//b", "/", "a/./b", "a/../b", "../a", "a/.."])
    fun `config names reject empty, dot and dot-dot segments`(name: String) {
        Fixtures.config.withMeta { this.name = name }.violations()
            .assert().containsExactly("meta.name" to ViolationCode.VIOLATION_CODE_INVALID_FORMAT)
    }

    @Test
    fun `instance names reject dot and dot-dot`() {
        listOf(".", "..").forEach {
            Fixtures.instance.withMeta { name = it }.violations()
                .assert().containsExactly("meta.name" to ViolationCode.VIOLATION_CODE_INVALID_FORMAT)
        }
        Fixtures.instance.withMeta { name = "..." }.violations().assert().isEmpty()
        Fixtures.instance.withMeta { name = ".a" }.violations().assert().isEmpty()
    }

    @Test
    fun `instance names allow up to 253 characters without slashes`() {
        Fixtures.instance.withMeta { name = "a".repeat(253) }.violations().assert().isEmpty()
        Fixtures.instance.withMeta { name = "a".repeat(254) }.violations()
            .assert().containsExactly("meta.name" to ViolationCode.VIOLATION_CODE_TOO_LONG)
        Fixtures.instance.withMeta { name = "a/b" }.violations()
            .assert().containsExactly("meta.name" to ViolationCode.VIOLATION_CODE_INVALID_FORMAT)
    }

    @Test
    fun `global object names follow the DNS-style rule`() {
        listOf(Fixtures.serviceAccount, Fixtures.role, Fixtures.roleBinding).forEach {
            it.withMeta { name = "a".repeat(63) }.violations().assert().isEmpty()
            it.withMeta { name = "a".repeat(64) }.violations()
                .assert().containsExactly("meta.name" to ViolationCode.VIOLATION_CODE_TOO_LONG)
            it.withMeta { name = "Admin" }.violations()
                .assert().containsExactly("meta.name" to ViolationCode.VIOLATION_CODE_INVALID_FORMAT)
        }
    }

    // --- meta.labels ---

    @Test
    fun `at most 32 labels`() {
        Fixtures.config.withMeta { labels.putAll(entries(32)) }.violations().assert().isEmpty()
        Fixtures.config.withMeta { labels.putAll(entries(33)) }.violations()
            .assert().containsExactly("meta.labels" to ViolationCode.VIOLATION_CODE_TOO_MANY)
    }

    @Test
    fun `label keys are at most 63 characters and not empty`() {
        Fixtures.config.withMeta { labels.put("k".repeat(63), "v") }.violations().assert().isEmpty()
        Fixtures.config.withMeta { labels.put("k".repeat(64), "v") }.violations()
            .assert().containsExactly("meta.labels[\"${"k".repeat(64)}\"]" to ViolationCode.VIOLATION_CODE_TOO_LONG)
        Fixtures.config.withMeta { labels.put("", "v") }.violations()
            .assert().containsExactly("meta.labels[\"\"]" to ViolationCode.VIOLATION_CODE_REQUIRED)
    }

    @Test
    fun `label values are at most 253 characters and may be empty`() {
        Fixtures.config.withMeta { labels.put("team", "") }.violations().assert().isEmpty()
        Fixtures.config.withMeta { labels.put("team", "v".repeat(253)) }.violations().assert().isEmpty()
        Fixtures.config.withMeta { labels.put("team", "v".repeat(254)) }.violations()
            .assert().containsExactly("meta.labels[\"team\"]" to ViolationCode.VIOLATION_CODE_TOO_LONG)
    }

    @Test
    fun `label value lengths count code points`() {
        val emoji = "😀"
        Fixtures.config.withMeta { labels.put("team", emoji.repeat(253)) }.violations().assert().isEmpty()
        Fixtures.config.withMeta { labels.put("team", emoji.repeat(254)) }.violations()
            .assert().containsExactly("meta.labels[\"team\"]" to ViolationCode.VIOLATION_CODE_TOO_LONG)
    }

    @ParameterizedTest
    @ValueSource(strings = ["a", "A", "0", "app.kubernetes.io", "team_name", "zone-1", "a-_.b"])
    fun `label and metadata keys accept the key rule`(key: String) {
        Fixtures.config.withMeta { labels.put(key, "v") }.violations().assert().isEmpty()
        Fixtures.instance.withInstance { putMetadata(key, "v") }.violations().assert().isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["-a", "a-", "_a", "a_", ".a", "a.", "a/b", "a b", "a:b", "é", "😀"])
    fun `label and metadata keys reject other keys`(key: String) {
        Fixtures.config.withMeta { labels.put(key, "v") }.violations()
            .assert().containsExactly("meta.labels[\"$key\"]" to ViolationCode.VIOLATION_CODE_INVALID_FORMAT)
        Fixtures.instance.withInstance { putMetadata(key, "v") }.violations()
            .assert().containsExactly("instance.metadata[\"$key\"]" to ViolationCode.VIOLATION_CODE_INVALID_FORMAT)
    }

    @Test
    fun `label keys are escaped in field paths`() {
        Fixtures.config.withMeta { labels.put("a\"b\\" + "k".repeat(63), "v") }.violations()
            .assert().containsExactly(
                "meta.labels[\"a\\\"b\\\\${"k".repeat(63)}\"]" to ViolationCode.VIOLATION_CODE_TOO_LONG
            )
    }

    @Test
    fun `a key and its value can both be too long`() {
        Fixtures.config.withMeta { labels.put("k".repeat(64), "v".repeat(254)) }.violations().assert().containsExactly(
            "meta.labels[\"${"k".repeat(64)}\"]" to ViolationCode.VIOLATION_CODE_TOO_LONG,
            "meta.labels[\"${"k".repeat(64)}\"]" to ViolationCode.VIOLATION_CODE_TOO_LONG,
        )
    }

    // --- meta.lease ---

    @Test
    fun `only instances can hold a lease`() {
        Fixtures.instance.withMeta { lease = 0 }.violations().assert().isEmpty()
        listOf(Fixtures.config, Fixtures.namespace, Fixtures.serviceAccount, Fixtures.role, Fixtures.roleBinding)
            .forEach {
                it.withMeta { lease = 1 }.violations().assert().containsExactly(
                    "meta.lease" to ViolationCode.VIOLATION_CODE_NOT_ALLOWED,
                )
            }
    }

    @Test
    fun `lease must not be negative`() {
        Fixtures.instance.withMeta { lease = -1 }.violations()
            .assert().containsExactly("meta.lease" to ViolationCode.VIOLATION_CODE_OUT_OF_RANGE)
        Fixtures.config.withMeta { lease = -1 }.violations()
            .assert().containsExactly("meta.lease" to ViolationCode.VIOLATION_CODE_OUT_OF_RANGE)
    }

    // --- config ---

    @Test
    fun `config format is required`() {
        Fixtures.config.copy { config = config.copy { format = ConfigFormat.CONFIG_FORMAT_UNSPECIFIED } }.violations()
            .assert().containsExactly("config.format" to ViolationCode.VIOLATION_CODE_REQUIRED)
    }

    @Test
    fun `unknown config format is out of range`() {
        Fixtures.config.copy { config = config.toBuilder().setFormatValue(99).build() }.violations()
            .assert().containsExactly("config.format" to ViolationCode.VIOLATION_CODE_OUT_OF_RANGE)
    }

    @Test
    fun `config content is at most 1 MiB`() {
        val limit = 1024 * 1024
        Fixtures.config.withContent("a".repeat(limit)).violations().assert().isEmpty()
        Fixtures.config.withContent("a".repeat(limit + 1)).violations()
            .assert().containsExactly("config.content" to ViolationCode.VIOLATION_CODE_TOO_LONG)
        Fixtures.config.withContent("").violations().assert().isEmpty()
    }

    @Test
    fun `config content size counts UTF-8 bytes`() {
        val limit = 1024 * 1024
        // "é" is two bytes in UTF-8.
        Fixtures.config.withContent("é".repeat(limit / 2)).violations().assert().isEmpty()
        Fixtures.config.withContent("é".repeat(limit / 2) + "a").violations()
            .assert().containsExactly("config.content" to ViolationCode.VIOLATION_CODE_TOO_LONG)
    }

    // --- instance ---

    @Test
    fun `instance service is required and follows the service rule`() {
        Fixtures.instance.withInstance { service = "" }.violations()
            .assert().containsExactly("instance.service" to ViolationCode.VIOLATION_CODE_REQUIRED)
        Fixtures.instance.withInstance { service = "a".repeat(63) }.violations().assert().isEmpty()
        Fixtures.instance.withInstance { service = "a".repeat(64) }.violations()
            .assert().containsExactly("instance.service" to ViolationCode.VIOLATION_CODE_TOO_LONG)
        Fixtures.instance.withInstance { service = "Order" }.violations()
            .assert().containsExactly("instance.service" to ViolationCode.VIOLATION_CODE_INVALID_FORMAT)
    }

    @Test
    fun `instance endpoint is required`() {
        Fixtures.instance.withInstance { clearEndpoint() }.violations()
            .assert().containsExactly("instance.endpoint" to ViolationCode.VIOLATION_CODE_REQUIRED)
    }

    @Test
    fun `endpoint scheme host and port are required`() {
        Fixtures.instance.withInstance { endpoint = endpoint {} }.violations().assert().containsExactly(
            "instance.endpoint.scheme" to ViolationCode.VIOLATION_CODE_REQUIRED,
            "instance.endpoint.host" to ViolationCode.VIOLATION_CODE_REQUIRED,
            "instance.endpoint.port" to ViolationCode.VIOLATION_CODE_OUT_OF_RANGE,
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["http", "https", "grpc", "h2c", "coap+tcp", "x-custom.v1"])
    fun `endpoint scheme accepts RFC 3986 schemes`(scheme: String) {
        Fixtures.instance.withEndpoint { this.scheme = scheme }.violations().assert().isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["HTTP", "1http", "ht tp", "http://", "+a"])
    fun `endpoint scheme rejects other values`(scheme: String) {
        Fixtures.instance.withEndpoint { this.scheme = scheme }.violations()
            .assert().containsExactly("instance.endpoint.scheme" to ViolationCode.VIOLATION_CODE_INVALID_FORMAT)
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 80, 65535])
    fun `endpoint port within range`(port: Int) {
        Fixtures.instance.withEndpoint { this.port = port }.violations().assert().isEmpty()
    }

    @ParameterizedTest
    @ValueSource(ints = [Int.MIN_VALUE, -1, 0, 65536, Int.MAX_VALUE])
    fun `endpoint port out of range`(port: Int) {
        Fixtures.instance.withEndpoint { this.port = port }.violations()
            .assert().containsExactly("instance.endpoint.port" to ViolationCode.VIOLATION_CODE_OUT_OF_RANGE)
    }

    @Test
    fun `unset weight is valid, the server fills 1`() {
        Fixtures.instance.withInstance { clearWeight() }.violations().assert().isEmpty()
        Fixtures.instance.withInstance { clearWeight() }.instance.hasWeight().assert().isFalse()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "localhost", "order", "a.example.com", "A-1.example", "10.0.0.1", "255.255.255.255", "::1",
            "fe80::1", "2001:db8::8a2e:370:7334", "::ffff:192.0.2.1", "xn--bcher-kva.example"
        ],
    )
    fun `endpoint host accepts hostnames and IP literals`(host: String) {
        Fixtures.instance.withEndpoint { this.host = host }.violations().assert().isEmpty()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "[::1]", "fe80::1%eth0", "-a.com", "a-.com", "a..com", "a.com.", "under_score.com",
            "256.1.1.1", "1.2.3", "01.2.3.4", "host:8080", "a b", "http://a"
        ],
    )
    fun `endpoint host rejects everything else`(host: String) {
        Fixtures.instance.withEndpoint { this.host = host }.violations()
            .assert().containsExactly("instance.endpoint.host" to ViolationCode.VIOLATION_CODE_INVALID_FORMAT)
    }

    @Test
    fun `descriptions are at most 1024 characters`() {
        val ok = "\uD83D\uDE00".repeat(1024)
        val tooLong = "d".repeat(1025)
        Fixtures.namespace.copy { namespace = namespace.copy { description = ok } }.violations().assert().isEmpty()
        Fixtures.namespace.copy { namespace = namespace.copy { description = tooLong } }.violations()
            .assert().containsExactly("namespace.description" to ViolationCode.VIOLATION_CODE_TOO_LONG)
        Fixtures.serviceAccount.copy { serviceAccount = serviceAccount.copy { description = ok } }.violations()
            .assert().isEmpty()
        Fixtures.serviceAccount.copy { serviceAccount = serviceAccount.copy { description = tooLong } }.violations()
            .assert().containsExactly("service_account.description" to ViolationCode.VIOLATION_CODE_TOO_LONG)
        Fixtures.role.copy { role = role.copy { description = ok } }.violations().assert().isEmpty()
        Fixtures.role.copy { role = role.copy { description = tooLong } }.violations()
            .assert().containsExactly("role.description" to ViolationCode.VIOLATION_CODE_TOO_LONG)
    }

    @Test
    fun `weight zero drains but is valid, negative is not`() {
        Fixtures.instance.withInstance { weight = 0 }.violations().assert().isEmpty()
        Fixtures.instance.withInstance { weight = Int.MAX_VALUE }.violations().assert().isEmpty()
        Fixtures.instance.withInstance { weight = -1 }.violations()
            .assert().containsExactly("instance.weight" to ViolationCode.VIOLATION_CODE_OUT_OF_RANGE)
    }

    @Test
    fun `at most 64 metadata entries`() {
        Fixtures.instance.withInstance { putAllMetadata(entries(64)) }.violations().assert().isEmpty()
        Fixtures.instance.withInstance { putAllMetadata(entries(65)) }.violations()
            .assert().containsExactly("instance.metadata" to ViolationCode.VIOLATION_CODE_TOO_MANY)
    }

    @Test
    fun `metadata keys are at most 63 characters and values at most 1024`() {
        Fixtures.instance.withInstance { putMetadata("k".repeat(63), "v".repeat(1024)) }.violations()
            .assert().isEmpty()
        Fixtures.instance.withInstance { putMetadata("k".repeat(64), "v") }.violations()
            .assert().containsExactly(
                "instance.metadata[\"${"k".repeat(64)}\"]" to ViolationCode.VIOLATION_CODE_TOO_LONG
            )
        Fixtures.instance.withInstance { putMetadata("zone", "v".repeat(1025)) }.violations()
            .assert().containsExactly("instance.metadata[\"zone\"]" to ViolationCode.VIOLATION_CODE_TOO_LONG)
        Fixtures.instance.withInstance { putMetadata("", "v") }.violations()
            .assert().containsExactly("instance.metadata[\"\"]" to ViolationCode.VIOLATION_CODE_REQUIRED)
    }

    // --- service account ---

    @Test
    fun `at most 2 tokens`() {
        val token = TokenRef.newBuilder().setId("t").build()
        Fixtures.serviceAccount.copy { serviceAccount = serviceAccount.copy { tokens += listOf(token, token) } }
            .violations().assert().isEmpty()
        Fixtures.serviceAccount.copy { serviceAccount = serviceAccount.copy { tokens += listOf(token, token, token) } }
            .violations().assert().containsExactly("service_account.tokens" to ViolationCode.VIOLATION_CODE_TOO_MANY)
    }

    // --- role ---

    @Test
    fun `a role without rules is valid`() {
        Fixtures.role.copy { role = role.toBuilder().clearRules().build() }.violations().assert().isEmpty()
    }

    @Test
    fun `rules need kinds namespaces and verbs`() {
        Fixtures.role.copy { role = role.toBuilder().clearRules().addRules(rule {}).build() }.violations()
            .assert().containsExactly(
                "role.rules[0].kinds" to ViolationCode.VIOLATION_CODE_REQUIRED,
                "role.rules[0].namespaces" to ViolationCode.VIOLATION_CODE_REQUIRED,
                "role.rules[0].verbs" to ViolationCode.VIOLATION_CODE_REQUIRED,
            )
    }

    @Test
    fun `rule namespaces accept the wildcard and the system namespace`() {
        Fixtures.role.withRule {
            kinds += Kind.KIND_ROLE
            namespaces += listOf("*", NamingRules.SYSTEM_NAMESPACE, "prod")
            verbs += Verb.VERB_PUT
        }.violations().assert().isEmpty()
    }

    @Test
    fun `rule elements are validated one by one`() {
        Fixtures.role.withRule {
            kinds += listOf(Kind.KIND_CONFIG, Kind.KIND_UNSPECIFIED)
            namespaces += listOf("prod", "Prod", "")
            verbs += listOf(Verb.VERB_UNSPECIFIED)
        }.violations().assert().containsExactly(
            "role.rules[1].kinds[1]" to ViolationCode.VIOLATION_CODE_REQUIRED,
            "role.rules[1].namespaces[1]" to ViolationCode.VIOLATION_CODE_INVALID_FORMAT,
            "role.rules[1].namespaces[2]" to ViolationCode.VIOLATION_CODE_REQUIRED,
            "role.rules[1].verbs[0]" to ViolationCode.VIOLATION_CODE_REQUIRED,
        )
    }

    @Test
    fun `unknown kinds and verbs are out of range`() {
        val unknown = Fixtures.role.role.rulesList.single().toBuilder().addKindsValue(99).addVerbsValue(99).build()
        Fixtures.role.copy { role = role.toBuilder().setRules(0, unknown).build() }.violations()
            .assert().containsExactly(
                "role.rules[0].kinds[1]" to ViolationCode.VIOLATION_CODE_OUT_OF_RANGE,
                "role.rules[0].verbs[1]" to ViolationCode.VIOLATION_CODE_OUT_OF_RANGE,
            )
    }

    @ParameterizedTest
    @ValueSource(strings = ["order", "order-*", "shared/*", "application.yaml", "a"])
    fun `rule names accept exact values and prefixes`(name: String) {
        Fixtures.role.withRule {
            kinds += Kind.KIND_CONFIG
            namespaces += "prod"
            verbs += Verb.VERB_GET
            names += name
        }.violations().assert().isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "*", "a*b", "a**", "*a", "a b", "a:b"])
    fun `rule names reject other patterns`(name: String) {
        Fixtures.role.withRule {
            kinds += Kind.KIND_CONFIG
            namespaces += "prod"
            verbs += Verb.VERB_GET
            names += name
        }.violations().assert().containsExactly("role.rules[1].names[0]" to ViolationCode.VIOLATION_CODE_INVALID_FORMAT)
    }

    @Test
    fun `rule names are at most 254 characters`() {
        Fixtures.role.withRule {
            kinds += Kind.KIND_CONFIG
            namespaces += "prod"
            verbs += Verb.VERB_GET
            names += "a".repeat(253) + "*"
            names += "a".repeat(255)
        }.violations().assert().containsExactly("role.rules[1].names[1]" to ViolationCode.VIOLATION_CODE_TOO_LONG)
    }

    @Test
    fun `at most 64 rule names`() {
        Fixtures.role.withRule {
            kinds += Kind.KIND_CONFIG
            namespaces += "prod"
            verbs += Verb.VERB_GET
            names += List(64) { "name-$it" }
        }.violations().assert().isEmpty()
        Fixtures.role.withRule {
            kinds += Kind.KIND_CONFIG
            namespaces += "prod"
            verbs += Verb.VERB_GET
            names += List(65) { "name-$it" }
        }.violations().assert().containsExactly("role.rules[1].names" to ViolationCode.VIOLATION_CODE_TOO_MANY)
    }

    // --- role binding ---

    @Test
    fun `role binding requires namespaces`() {
        Fixtures.roleBinding.copy { roleBinding = roleBinding.toBuilder().clearNamespaces().build() }.violations()
            .assert().containsExactly("role_binding.namespaces" to ViolationCode.VIOLATION_CODE_REQUIRED)
    }

    @Test
    fun `role binding namespaces accept the wildcard and the system namespace`() {
        Fixtures.roleBinding.copy {
            roleBinding = roleBinding.toBuilder()
                .clearNamespaces()
                .addAllNamespaces(listOf("*", NamingRules.SYSTEM_NAMESPACE, "prod"))
                .build()
        }.violations().assert().isEmpty()
    }

    @Test
    fun `role binding namespaces are validated one by one`() {
        Fixtures.roleBinding.copy {
            roleBinding = roleBinding.toBuilder().addAllNamespaces(listOf("Prod", "")).build()
        }.violations().assert().containsExactly(
            "role_binding.namespaces[1]" to ViolationCode.VIOLATION_CODE_INVALID_FORMAT,
            "role_binding.namespaces[2]" to ViolationCode.VIOLATION_CODE_REQUIRED,
        )
    }

    @Test
    fun `role binding requires a role and subjects`() {
        Fixtures.roleBinding.copy { roleBinding = roleBinding.toBuilder().clearRole().clearSubjects().build() }
            .violations().assert().containsExactly(
                "role_binding.role" to ViolationCode.VIOLATION_CODE_REQUIRED,
                "role_binding.subjects" to ViolationCode.VIOLATION_CODE_REQUIRED,
            )
    }

    @Test
    fun `role binding role follows the global name rule`() {
        Fixtures.roleBinding.copy { roleBinding = roleBinding.copy { role = "Admin" } }.violations()
            .assert().containsExactly("role_binding.role" to ViolationCode.VIOLATION_CODE_INVALID_FORMAT)
    }

    @Test
    fun `subjects need a type and a name`() {
        Fixtures.roleBinding.withSubject {}.violations().assert().containsExactly(
            "role_binding.subjects[1].type" to ViolationCode.VIOLATION_CODE_REQUIRED,
            "role_binding.subjects[1].name" to ViolationCode.VIOLATION_CODE_REQUIRED,
        )
    }

    @Test
    fun `service account subjects follow the global name rule`() {
        Fixtures.roleBinding.withSubject {
            type = Subject.Type.TYPE_SERVICE_ACCOUNT
            name = "Order"
        }.violations().assert().containsExactly(
            "role_binding.subjects[1].name" to ViolationCode.VIOLATION_CODE_INVALID_FORMAT
        )
    }

    @Test
    fun `user and group subjects take any OIDC claim value`() {
        Fixtures.roleBinding.withSubject {
            type = Subject.Type.TYPE_USER
            name = "Alice@Example.com"
        }.withSubject {
            type = Subject.Type.TYPE_GROUP
            name = "cn=ops,dc=example"
        }.violations().assert().isEmpty()
    }

    @Test
    fun `unknown subject type is out of range`() {
        val unknown = Subject.newBuilder().setTypeValue(99).setName("x").build()
        Fixtures.roleBinding.copy { roleBinding = roleBinding.toBuilder().addSubjects(unknown).build() }
            .violations().assert().containsExactly(
                "role_binding.subjects[1].type" to ViolationCode.VIOLATION_CODE_OUT_OF_RANGE
            )
    }

    @Test
    fun `custom limits apply`() {
        val strict = Validator(Limits(maxLabels = 1, maxConfigContentBytes = 3))
        strict.validate(Fixtures.config.withContent("abcd").withMeta { labels.putAll(entries(2)) }).codes()
            .assert().containsExactly(
                "meta.labels" to ViolationCode.VIOLATION_CODE_TOO_MANY,
                "config.content" to ViolationCode.VIOLATION_CODE_TOO_LONG,
            )
    }

    @Test
    fun `violations carry a description`() {
        validator.validate(Fixtures.config.withMeta { name = "" }).single().description.assert().isNotBlank()
    }

    private fun entries(count: Int): Map<String, String> = (1..count).associate { "k$it" to "v$it" }

    private fun Resource.withMeta(block: ObjectMetaKt.Dsl.() -> Unit): Resource =
        copy { meta = meta.copy(block) }

    private fun Resource.withContent(content: String): Resource =
        copy { config = config.toBuilder().setContentBytes(ByteString.copyFromUtf8(content)).build() }

    private fun Resource.withInstance(block: InstanceSpec.Builder.() -> Unit): Resource =
        copy { instance = instance.toBuilder().apply(block).build() }

    private fun Resource.withEndpoint(block: EndpointKt.Dsl.() -> Unit): Resource =
        withInstance { endpoint = endpoint.copy(block) }

    private fun Resource.withRule(block: RuleKt.Dsl.() -> Unit): Resource =
        copy { role = role.toBuilder().addRules(rule(block)).build() }

    private fun Resource.withSubject(block: SubjectKt.Dsl.() -> Unit): Resource =
        copy { roleBinding = roleBinding.toBuilder().addSubjects(subject(block)).build() }
}
