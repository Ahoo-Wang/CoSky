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

import com.google.protobuf.ProtocolMessageEnum
import me.ahoo.cosky.api.isGlobal
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.ViolationCode

/**
 * Field-level rules shared by the resource and request validators; collects violations instead of failing fast.
 */
internal class FieldChecks(private val limits: Limits) {
    private val violations = mutableListOf<Violation>()

    fun result(): List<Violation> = violations.toList()

    fun add(field: String, code: ViolationCode, description: String) {
        violations += Violation(field, code, description)
    }

    /**
     * Namespace a [kind] lives in: `_system` for global kinds, a valid user namespace otherwise.
     */
    fun namespaceOf(field: String, kind: Kind, namespace: String) {
        if (kind.isGlobal) {
            if (namespace != NamingRules.SYSTEM_NAMESPACE) {
                add(
                    field,
                    ViolationCode.VIOLATION_CODE_NOT_ALLOWED,
                    "$kind lives in the ${NamingRules.SYSTEM_NAMESPACE} namespace."
                )
            }
            return
        }
        userNamespace(field, namespace)
    }

    /**
     * A namespace that holds Configs and Instances: required, never `_system`.
     */
    fun userNamespace(field: String, namespace: String) {
        when {
            namespace.isEmpty() -> add(field, ViolationCode.VIOLATION_CODE_REQUIRED, "Namespace is required.")
            namespace == NamingRules.SYSTEM_NAMESPACE ->
                add(field, ViolationCode.VIOLATION_CODE_RESERVED_NAME, "${NamingRules.SYSTEM_NAMESPACE} is reserved.")

            else -> pattern(field, namespace, NamingRules.NAMESPACE, NamingRules.MAX_NAMESPACE_LENGTH, "namespace")
        }
    }

    /**
     * Name of an object of [kind]; `_system` is reserved for Namespace names.
     */
    fun nameOf(field: String, kind: Kind, name: String) {
        if (name.isEmpty()) {
            add(field, ViolationCode.VIOLATION_CODE_REQUIRED, "Name is required.")
            return
        }
        when (kind) {
            Kind.KIND_NAMESPACE -> userNamespace(field, name)
            Kind.KIND_CONFIG -> configName(field, name)
            Kind.KIND_INSTANCE -> instanceName(field, name)

            else -> pattern(field, name, NamingRules.GLOBAL_NAME, NamingRules.MAX_GLOBAL_NAME_LENGTH, "$kind name")
        }
    }

    private fun configName(field: String, name: String) {
        val matches = pattern(field, name, NamingRules.CONFIG_NAME, NamingRules.MAX_CONFIG_NAME_LENGTH, "Config name")
        if (matches && !NamingRules.isConfigName(name)) {
            add(
                field,
                ViolationCode.VIOLATION_CODE_INVALID_FORMAT,
                "Config name segments between '/' must not be empty, '.' or '..'.",
            )
        }
    }

    private fun instanceName(field: String, name: String) {
        val matches =
            pattern(field, name, NamingRules.INSTANCE_NAME, NamingRules.MAX_INSTANCE_NAME_LENGTH, "Instance name")
        if (matches && !NamingRules.isInstanceName(name)) {
            add(field, ViolationCode.VIOLATION_CODE_INVALID_FORMAT, "An Instance name must not be '.' or '..'.")
        }
    }

    fun service(field: String, service: String) {
        if (service.isEmpty()) {
            add(field, ViolationCode.VIOLATION_CODE_REQUIRED, "Service is required.")
            return
        }
        pattern(field, service, NamingRules.SERVICE, NamingRules.MAX_SERVICE_LENGTH, "service")
    }

    /**
     * Optional Instance-only service filter of List and Watch.
     */
    fun serviceFilter(field: String, kind: Kind, service: String) {
        if (service.isEmpty()) {
            return
        }
        if (kind != Kind.KIND_INSTANCE) {
            add(field, ViolationCode.VIOLATION_CODE_NOT_ALLOWED, "The service filter applies to INSTANCE only.")
            return
        }
        service(field, service)
    }

    /**
     * Returns whether [value] passed both the length and the pattern check.
     */
    fun pattern(field: String, value: String, regex: Regex, maxLength: Int, what: String): Boolean {
        when {
            value.length > maxLength ->
                add(field, ViolationCode.VIOLATION_CODE_TOO_LONG, "The $what exceeds $maxLength characters.")

            !regex.matches(value) ->
                add(field, ViolationCode.VIOLATION_CODE_INVALID_FORMAT, "The $what must match ${regex.pattern}.")

            else -> return true
        }
        return false
    }

    fun description(field: String, description: String) {
        if (description.codePointLength() > limits.maxDescriptionLength) {
            add(
                field,
                ViolationCode.VIOLATION_CODE_TOO_LONG,
                "The description exceeds ${limits.maxDescriptionLength} characters.",
            )
        }
    }

    /**
     * An enum that must be set to a known, non-zero value; returns whether it is.
     */
    fun requiredEnum(field: String, value: ProtocolMessageEnum, unspecified: ProtocolMessageEnum): Boolean {
        when {
            value == unspecified -> add(field, ViolationCode.VIOLATION_CODE_REQUIRED, "A value is required.")
            isUnrecognized(value) -> add(field, ViolationCode.VIOLATION_CODE_OUT_OF_RANGE, "Unknown value.")
            else -> return true
        }
        return false
    }

    /**
     * Returns whether [kind] is a known kind, so that kind-dependent rules can apply.
     */
    fun kind(field: String, kind: Kind): Boolean = requiredEnum(field, kind, Kind.KIND_UNSPECIFIED)

    fun labels(field: String, labels: Map<String, String>) {
        entries(
            field = field,
            entries = labels,
            maxEntries = limits.maxLabels,
            maxKeyLength = limits.maxLabelKeyLength,
            maxValueLength = limits.maxLabelValueLength,
        )
    }

    fun metadata(field: String, metadata: Map<String, String>) {
        entries(
            field = field,
            entries = metadata,
            maxEntries = limits.maxMetadataEntries,
            maxKeyLength = limits.maxMetadataKeyLength,
            maxValueLength = limits.maxMetadataValueLength,
        )
    }

    private fun entries(
        field: String,
        entries: Map<String, String>,
        maxEntries: Int,
        maxKeyLength: Int,
        maxValueLength: Int,
    ) {
        if (entries.size > maxEntries) {
            add(field, ViolationCode.VIOLATION_CODE_TOO_MANY, "At most $maxEntries entries are allowed.")
        }
        entries.forEach { (key, value) ->
            val entryField = mapEntry(field, key)
            when {
                key.isEmpty() -> add(entryField, ViolationCode.VIOLATION_CODE_REQUIRED, "The key must not be empty.")
                key.length > maxKeyLength ->
                    add(entryField, ViolationCode.VIOLATION_CODE_TOO_LONG, "The key exceeds $maxKeyLength characters.")

                !NamingRules.ENTRY_KEY.matches(key) -> add(
                    entryField,
                    ViolationCode.VIOLATION_CODE_INVALID_FORMAT,
                    "The key must match ${NamingRules.ENTRY_KEY.pattern}.",
                )
            }
            if (value.codePointLength() > maxValueLength) {
                add(entryField, ViolationCode.VIOLATION_CODE_TOO_LONG, "The value exceeds $maxValueLength characters.")
            }
        }
    }

    fun revision(field: String, revision: Long) {
        if (revision < 0) {
            add(field, ViolationCode.VIOLATION_CODE_OUT_OF_RANGE, "The revision must not be negative.")
        }
    }

    fun pageSize(field: String, pageSize: Int) {
        if (pageSize !in 0..limits.maxPageSize) {
            add(
                field,
                ViolationCode.VIOLATION_CODE_OUT_OF_RANGE,
                "The page size must be 0 (default ${limits.defaultPageSize}) or within [1, ${limits.maxPageSize}].",
            )
        }
    }

    /**
     * The token is opaque to clients; only the server can judge whether it is still valid (§4.6).
     */
    fun pageToken(field: String, pageToken: String) {
        if (pageToken.length > limits.maxPageTokenLength) {
            add(
                field,
                ViolationCode.VIOLATION_CODE_TOO_LONG,
                "The page token exceeds ${limits.maxPageTokenLength} characters.",
            )
        }
    }

    fun lease(field: String, lease: Long) {
        when {
            lease == 0L -> add(field, ViolationCode.VIOLATION_CODE_REQUIRED, "A lease is required.")
            lease < 0 -> add(field, ViolationCode.VIOLATION_CODE_OUT_OF_RANGE, "The lease must be positive.")
        }
    }

    fun leaseTtl(field: String, ttlSeconds: Int) {
        if (ttlSeconds != 0 && ttlSeconds !in limits.minLeaseTtlSeconds..limits.maxLeaseTtlSeconds) {
            add(
                field,
                ViolationCode.VIOLATION_CODE_OUT_OF_RANGE,
                "The TTL must be 0 (default ${limits.defaultLeaseTtlSeconds}) or within " +
                    "[${limits.minLeaseTtlSeconds}, ${limits.maxLeaseTtlSeconds}] seconds.",
            )
        }
    }

    private fun isUnrecognized(value: ProtocolMessageEnum): Boolean =
        (value as Enum<*>).name == UNRECOGNIZED

    private companion object {
        const val UNRECOGNIZED = "UNRECOGNIZED"
    }
}

internal fun path(parent: String, child: String): String = if (parent.isEmpty()) child else "$parent.$child"

internal fun mapEntry(field: String, key: String): String {
    val escaped = key.replace("\\", "\\\\").replace("\"", "\\\"")
    return "$field[\"$escaped\"]"
}

internal fun element(field: String, index: Int): String = "$field[$index]"

internal fun String.codePointLength(): Int = codePointCount(0, length)
