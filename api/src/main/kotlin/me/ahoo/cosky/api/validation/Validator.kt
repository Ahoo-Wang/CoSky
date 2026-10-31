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

import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.DeleteRequest
import me.ahoo.cosky.api.v1.GetRequest
import me.ahoo.cosky.api.v1.GrantRequest
import me.ahoo.cosky.api.v1.HistoryRequest
import me.ahoo.cosky.api.v1.KeepAliveRequest
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.ListRequest
import me.ahoo.cosky.api.v1.PutRequest
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.RevokeRequest
import me.ahoo.cosky.api.v1.ServicesRequest
import me.ahoo.cosky.api.v1.StatsRequest
import me.ahoo.cosky.api.v1.TopologyRequest
import me.ahoo.cosky.api.v1.ViolationCode
import me.ahoo.cosky.api.v1.WatchRequest

/**
 * Validates resources and requests against the naming rules (§4.2) and limits (§7.1).
 *
 * Every `validate` returns all violations it finds, empty when the input is valid; [requireValid] turns them into an
 * `INVALID_ARGUMENT` [CoSkyException]. Field paths use proto field names relative to the validated message.
 * Revisions and leases that only the server can judge (existence, CAS) are not checked here.
 */
class Validator(private val limits: Limits = Limits.DEFAULT) {

    fun validate(resource: Resource): List<Violation> = collect { resource(it, "", resource) }

    fun validate(key: ResourceKey): List<Violation> = collect { key(it, "", key) }

    fun validate(request: GetRequest): List<Violation> = collect {
        requiredKey(it, request.hasKey(), request.key)
        it.revision("min_revision", request.minRevision)
    }

    fun validate(request: ListRequest): List<Violation> = collect {
        if (it.kind("kind", request.kind)) {
            it.namespaceOf("namespace", request.kind, request.namespace)
        }
        it.labels("label_selector", request.labelSelectorMap)
        it.serviceFilter("service", request.kind, request.service)
        it.revision("min_revision", request.minRevision)
        it.pageSize("page_size", request.pageSize)
        it.pageToken("page_token", request.pageToken)
    }

    fun validate(request: PutRequest): List<Violation> = collect {
        if (request.hasResource()) {
            resource(it, "resource", request.resource)
        } else {
            it.add("resource", ViolationCode.VIOLATION_CODE_REQUIRED, "A resource is required.")
        }
        if (request.expectedRevision < MUST_NOT_EXIST) {
            it.add(
                "expected_revision",
                ViolationCode.VIOLATION_CODE_OUT_OF_RANGE,
                "The expected revision must be -1 (must not exist), 0 (no check) or positive (compare-and-set).",
            )
        }
    }

    fun validate(request: DeleteRequest): List<Violation> = collect {
        requiredKey(it, request.hasKey(), request.key)
        it.revision("expected_revision", request.expectedRevision)
    }

    fun validate(request: HistoryRequest): List<Violation> = collect {
        requiredKey(it, request.hasKey(), request.key)
        if (request.key.kind in HISTORY_EXCLUDED_KINDS) {
            it.add("key.kind", ViolationCode.VIOLATION_CODE_NOT_ALLOWED, "History is kept for CONFIG only.")
        }
        if (request.limit < 0) {
            it.add("limit", ViolationCode.VIOLATION_CODE_OUT_OF_RANGE, "The limit must not be negative.")
        }
        it.pageToken("page_token", request.pageToken)
    }

    fun validate(request: WatchRequest): List<Violation> = collect {
        when (request.requestCase) {
            WatchRequest.RequestCase.CREATE -> watchCreate(it, "create", request.create)
            WatchRequest.RequestCase.CANCEL -> Unit
            else -> it.add("request", ViolationCode.VIOLATION_CODE_REQUIRED, "Either create or cancel is required.")
        }
    }

    fun validate(request: GrantRequest): List<Violation> = collect {
        it.userNamespace("namespace", request.namespace)
        it.leaseTtl("ttl_seconds", request.ttlSeconds)
    }

    fun validate(request: KeepAliveRequest): List<Violation> = collect {
        it.userNamespace("namespace", request.namespace)
        it.lease("lease", request.lease)
    }

    fun validate(request: RevokeRequest): List<Violation> = collect {
        it.userNamespace("namespace", request.namespace)
        it.lease("lease", request.lease)
    }

    fun validate(request: ServicesRequest): List<Violation> = namespaceOnly(request.namespace)

    fun validate(request: TopologyRequest): List<Violation> = namespaceOnly(request.namespace)

    fun validate(request: StatsRequest): List<Violation> = namespaceOnly(request.namespace)

    private fun namespaceOnly(namespace: String): List<Violation> = collect { it.userNamespace("namespace", namespace) }

    private fun resource(checks: FieldChecks, field: String, resource: Resource) {
        ResourceChecks(checks, limits).resource(field, resource)
    }

    private fun requiredKey(checks: FieldChecks, present: Boolean, key: ResourceKey) {
        if (present) {
            key(checks, "key", key)
        } else {
            checks.add("key", ViolationCode.VIOLATION_CODE_REQUIRED, "A key is required.")
        }
    }

    private fun key(checks: FieldChecks, field: String, key: ResourceKey) {
        if (!checks.kind(path(field, "kind"), key.kind)) {
            return
        }
        checks.namespaceOf(path(field, "namespace"), key.kind, key.namespace)
        checks.nameOf(path(field, "name"), key.kind, key.name)
    }

    private fun watchCreate(checks: FieldChecks, field: String, create: WatchRequest.Create) {
        if (checks.kind(path(field, "kind"), create.kind)) {
            checks.namespaceOf(path(field, "namespace"), create.kind, create.namespace)
            if (create.name.isNotEmpty()) {
                checks.nameOf(path(field, "name"), create.kind, create.name)
            }
        }
        checks.serviceFilter(path(field, "service"), create.kind, create.service)
        checks.revision(path(field, "from_revision"), create.fromRevision)
        if (create.fromRevision > 0 && create.fromEpoch.isEmpty()) {
            checks.add(
                path(field, "from_epoch"),
                ViolationCode.VIOLATION_CODE_REQUIRED,
                "Resuming from a revision requires its epoch.",
            )
        }
    }

    private inline fun collect(block: (FieldChecks) -> Unit): List<Violation> {
        val checks = FieldChecks(limits)
        block(checks)
        return checks.result()
    }

    companion object {
        /** `expected_revision` meaning "the resource must not exist yet". */
        const val MUST_NOT_EXIST = -1L

        val DEFAULT = Validator()

        private val HISTORY_EXCLUDED_KINDS = Kind.entries.toSet() -
            setOf(Kind.KIND_CONFIG, Kind.KIND_UNSPECIFIED, Kind.UNRECOGNIZED)
    }
}

/**
 * Throws an `INVALID_ARGUMENT` [CoSkyException] carrying [violations] when there are any.
 */
fun requireValid(violations: List<Violation>) {
    if (violations.isNotEmpty()) {
        throw CoSkyException.invalidArgument(violations)
    }
}
