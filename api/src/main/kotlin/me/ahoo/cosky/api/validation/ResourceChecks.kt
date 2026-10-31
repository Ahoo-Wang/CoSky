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

import me.ahoo.cosky.api.kind
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.ConfigSpec
import me.ahoo.cosky.api.v1.InstanceSpec
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.ObjectMeta
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.RoleBindingSpec
import me.ahoo.cosky.api.v1.RoleSpec
import me.ahoo.cosky.api.v1.ServiceAccountSpec
import me.ahoo.cosky.api.v1.Subject
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.api.v1.ViolationCode

/**
 * Validates a [Resource] as written by a client: naming rules (§4.2) and limits (§7.1).
 *
 * Read-only fields (`revision`, `create_revision`, timestamps, `updated_by`, `sha256`) are ignored: the server
 * overwrites them (§7.1).
 */
internal class ResourceChecks(private val checks: FieldChecks, private val limits: Limits) {

    fun resource(field: String, resource: Resource) {
        val kind = resource.kind
        if (kind == Kind.KIND_UNSPECIFIED) {
            checks.add(
                path(field, "spec"),
                ViolationCode.VIOLATION_CODE_REQUIRED,
                "Exactly one spec must be set; it decides the kind.",
            )
        }
        if (!resource.hasMeta()) {
            checks.add(path(field, "meta"), ViolationCode.VIOLATION_CODE_REQUIRED, "Metadata is required.")
        } else if (kind != Kind.KIND_UNSPECIFIED) {
            meta(path(field, "meta"), kind, resource.meta)
        }
        when (kind) {
            Kind.KIND_NAMESPACE -> checks.description(
                path(field, "namespace.description"),
                resource.namespace.description,
            )
            Kind.KIND_CONFIG -> config(path(field, "config"), resource.config)
            Kind.KIND_INSTANCE -> instance(path(field, "instance"), resource.instance)
            Kind.KIND_SERVICE_ACCOUNT -> serviceAccount(path(field, "service_account"), resource.serviceAccount)
            Kind.KIND_ROLE -> role(path(field, "role"), resource.role)
            Kind.KIND_ROLE_BINDING -> roleBinding(path(field, "role_binding"), resource.roleBinding)
            else -> Unit
        }
    }

    private fun meta(field: String, kind: Kind, meta: ObjectMeta) {
        checks.namespaceOf(path(field, "namespace"), kind, meta.namespace)
        checks.nameOf(path(field, "name"), kind, meta.name)
        checks.labels(path(field, "labels"), meta.labelsMap)
        val leaseField = path(field, "lease")
        when {
            meta.lease < 0 -> checks.add(
                leaseField,
                ViolationCode.VIOLATION_CODE_OUT_OF_RANGE,
                "The lease must not be negative.",
            )
            meta.lease != 0L && kind != Kind.KIND_INSTANCE ->
                checks.add(
                    leaseField,
                    ViolationCode.VIOLATION_CODE_NOT_ALLOWED,
                    "Only an Instance can be attached to a lease.",
                )
        }
    }

    private fun config(field: String, spec: ConfigSpec) {
        checks.requiredEnum(path(field, "format"), spec.format, ConfigFormat.CONFIG_FORMAT_UNSPECIFIED)
        if (spec.contentBytes.size() > limits.maxConfigContentBytes) {
            checks.add(
                path(field, "content"),
                ViolationCode.VIOLATION_CODE_TOO_LONG,
                "The content exceeds ${limits.maxConfigContentBytes} bytes (UTF-8).",
            )
        }
    }

    private fun instance(field: String, spec: InstanceSpec) {
        checks.service(path(field, "service"), spec.service)
        endpoint(path(field, "endpoint"), spec)
        // Unset means 1 (the server fills it); 0 drains the instance.
        if (spec.weight < 0) {
            checks.add(
                path(field, "weight"),
                ViolationCode.VIOLATION_CODE_OUT_OF_RANGE,
                "The weight must not be negative.",
            )
        }
        checks.metadata(path(field, "metadata"), spec.metadataMap)
    }

    private fun endpoint(field: String, spec: InstanceSpec) {
        if (!spec.hasEndpoint()) {
            checks.add(field, ViolationCode.VIOLATION_CODE_REQUIRED, "An endpoint is required.")
            return
        }
        val endpoint = spec.endpoint
        val schemeField = path(field, "scheme")
        when {
            endpoint.scheme.isEmpty() -> checks.add(
                schemeField,
                ViolationCode.VIOLATION_CODE_REQUIRED,
                "A scheme is required.",
            )
            !SCHEME.matches(endpoint.scheme) ->
                checks.add(
                    schemeField,
                    ViolationCode.VIOLATION_CODE_INVALID_FORMAT,
                    "The scheme must match ${SCHEME.pattern}."
                )
        }
        val hostField = path(field, "host")
        when {
            endpoint.host.isEmpty() -> checks.add(
                hostField,
                ViolationCode.VIOLATION_CODE_REQUIRED,
                "A host is required.",
            )
            !HostRules.isHost(endpoint.host) -> checks.add(
                hostField,
                ViolationCode.VIOLATION_CODE_INVALID_FORMAT,
                "The host must be an RFC 1123 hostname, an IPv4 literal or an IPv6 literal without brackets.",
            )
        }
        if (endpoint.port !in MIN_PORT..MAX_PORT) {
            checks.add(
                path(field, "port"),
                ViolationCode.VIOLATION_CODE_OUT_OF_RANGE,
                "The port must be within [1, 65535]."
            )
        }
    }

    private fun serviceAccount(field: String, spec: ServiceAccountSpec) {
        checks.description(path(field, "description"), spec.description)
        if (spec.tokensCount > limits.maxTokens) {
            checks.add(
                path(field, "tokens"),
                ViolationCode.VIOLATION_CODE_TOO_MANY,
                "At most ${limits.maxTokens} tokens are allowed."
            )
        }
    }

    private fun role(field: String, spec: RoleSpec) {
        checks.description(path(field, "description"), spec.description)
        spec.rulesList.forEachIndexed { index, rule ->
            val ruleField = element(path(field, "rules"), index)
            nonEmpty(path(ruleField, "kinds"), rule.kindsList) { itemField, kind ->
                checks.requiredEnum(itemField, kind, Kind.KIND_UNSPECIFIED)
            }
            nonEmpty(path(ruleField, "namespaces"), rule.namespacesList, ::namespaceScope)
            nonEmpty(path(ruleField, "verbs"), rule.verbsList) { itemField, verb ->
                checks.requiredEnum(itemField, verb, Verb.VERB_UNSPECIFIED)
            }
            ruleNames(path(ruleField, "names"), rule.namesList)
        }
    }

    private fun ruleNames(field: String, names: List<String>) {
        if (names.size > limits.maxRuleNames) {
            checks.add(
                field,
                ViolationCode.VIOLATION_CODE_TOO_MANY,
                "At most ${limits.maxRuleNames} names are allowed.",
            )
        }
        names.forEachIndexed { index, name ->
            checks.pattern(
                element(field, index),
                name,
                NamingRules.RULE_NAME,
                NamingRules.MAX_RULE_NAME_LENGTH,
                "rule name",
            )
        }
    }

    /**
     * An entry of `Rule.namespaces` or `RoleBindingSpec.namespaces`: the wildcard, `_system` or a user namespace.
     */
    private fun namespaceScope(field: String, namespace: String) {
        if (namespace != NamingRules.ANY_NAMESPACE && namespace != NamingRules.SYSTEM_NAMESPACE) {
            checks.userNamespace(field, namespace)
        }
    }

    private fun roleBinding(field: String, spec: RoleBindingSpec) {
        checks.nameOf(path(field, "role"), Kind.KIND_ROLE, spec.role)
        nonEmpty(path(field, "subjects"), spec.subjectsList) { subjectField, subject ->
            checks.requiredEnum(path(subjectField, "type"), subject.type, Subject.Type.TYPE_UNSPECIFIED)
            val nameField = path(subjectField, "name")
            if (subject.type == Subject.Type.TYPE_SERVICE_ACCOUNT) {
                checks.nameOf(nameField, Kind.KIND_SERVICE_ACCOUNT, subject.name)
            } else if (subject.name.isEmpty()) {
                checks.add(nameField, ViolationCode.VIOLATION_CODE_REQUIRED, "A subject name is required.")
            }
        }
        nonEmpty(path(field, "namespaces"), spec.namespacesList, ::namespaceScope)
    }

    private inline fun <T> nonEmpty(field: String, items: List<T>, check: (String, T) -> Unit) {
        if (items.isEmpty()) {
            checks.add(field, ViolationCode.VIOLATION_CODE_REQUIRED, "At least one value is required.")
        }
        items.forEachIndexed { index, item -> check(element(field, index), item) }
    }

    private companion object {
        /** RFC 3986 scheme, lower case. */
        val SCHEME = Regex("^[a-z][a-z0-9+.-]*$")
        const val MIN_PORT = 1
        const val MAX_PORT = 65535
    }
}
