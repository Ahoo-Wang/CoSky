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

package me.ahoo.cosky.server.resource

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.kind
import me.ahoo.cosky.api.v1.DeleteRequest
import me.ahoo.cosky.api.v1.DeleteResponse
import me.ahoo.cosky.api.v1.GetRequest
import me.ahoo.cosky.api.v1.HistoryRequest
import me.ahoo.cosky.api.v1.HistoryResponse
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.ListRequest
import me.ahoo.cosky.api.v1.ListResponse
import me.ahoo.cosky.api.v1.PutRequest
import me.ahoo.cosky.api.v1.PutResponse
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.api.validation.Limits
import me.ahoo.cosky.api.validation.Validator
import me.ahoo.cosky.api.validation.requireValid
import me.ahoo.cosky.server.audit.AuditLogger
import me.ahoo.cosky.server.auth.Authorizer
import me.ahoo.cosky.server.auth.Caller
import me.ahoo.cosky.server.auth.Permission
import me.ahoo.cosky.server.auth.Principal
import me.ahoo.cosky.server.auth.authorizedName
import me.ahoo.cosky.server.watch.NamespaceView
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.server.watch.chunkedByBytes
import me.ahoo.cosky.store.ApplyResult
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.TerminateResult
import me.ahoo.cosky.store.key
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The resource operations (design §4.3, §7.1, §7.2), behind both protocols. Reads come from the Watch Cache, and only
 * writes and history go to the store.
 */
class ResourceService(
    private val store: Store,
    private val caches: WatchCache,
    private val limits: Limits = Limits.DEFAULT,
    private val minRevisionTimeout: Duration = 3.seconds,
    private val authorizer: Authorizer = Authorizer.ALLOW_ALL,
    private val audit: AuditLogger = AuditLogger(),
) {
    private val validator = Validator(limits)

    suspend fun get(request: GetRequest, caller: Caller): Resource {
        requireValid(validator.validate(request))
        val key = request.key
        val permission = permissionOf(caller.principal, Verb.VERB_GET, key.kind, key.namespace)
        // An Instance is authorized by its service, which takes reading it.
        if (key.kind != Kind.KIND_INSTANCE) {
            requireAllowed(permission, key.name)
        }
        val resource = viewOf(key.namespace, request.minRevision).objects[key]
            ?: throw CoSkyException.resourceNotFound(key)
        requireAllowed(permission, resource.authorizedName)
        return resource
    }

    /**
     * A rule limited to some names filters the list rather than refusing it (design §7.8).
     */
    suspend fun list(request: ListRequest, caller: Caller): ListResponse {
        requireValid(validator.validate(request))
        val permission = permissionOf(caller.principal, Verb.VERB_GET, request.kind, request.namespace)
        val token = PageTokens.list(request.pageToken)
        val view = viewOf(request.namespace, request.minRevision)
        if (token != null && token.epoch != view.epoch) {
            throw PageTokens.invalid("The timeline changed since this page was requested; list from the first page.")
        }
        val pageSize = request.pageSize.takeIf { it != 0 } ?: limits.defaultPageSize
        val page = view.objects.values.asSequence()
            .filter { it.meta.name > token?.after.orEmpty() && matches(it, request) }
            .filter { permission.allows(it.authorizedName) }
            .sortedBy { it.meta.name }
            .take(pageSize + 1)
            .toList()
            .chunkedByBytes(limits.maxMessageBytes) { it.serializedSize }
        val items = page.firstOrNull().orEmpty().take(pageSize)
        val revision = token?.revision ?: view.revision
        return ListResponse.newBuilder()
            .addAllItems(items)
            .setRevision(revision)
            .setEpoch(view.epoch)
            .setNextPageToken(
                if (page.sumOf { it.size } > items.size) {
                    PageTokens.encode(ListPageToken(view.epoch, revision, items.last().meta.name))
                } else {
                    ""
                },
            )
            .build()
    }

    suspend fun put(request: PutRequest, caller: Caller): PutResponse {
        requireValid(validator.validate(request))
        val key = request.resource.key
        return audited(caller, Verb.VERB_PUT, key, PutResponse::getRevision) {
            authorizePut(request.resource, key, caller.principal)
            if (key.kind == Kind.KIND_NAMESPACE) {
                store.createNamespace(key.name)
            }
            val write = withStoredTokens(request)
            val applied = apply(
                key,
                Mutation.Put(write.resource, write.expectedRevision),
                write.expectedRevision,
                caller.principal.id,
            )
            PutResponse.newBuilder().setRevision(applied.revision).setChanged(applied.changed > 0).build()
        }
    }

    suspend fun delete(request: DeleteRequest, caller: Caller): DeleteResponse {
        requireValid(validator.validate(request))
        val key = request.key
        return audited(caller, Verb.VERB_DELETE, key, DeleteResponse::getRevision) {
            authorizeDelete(key, caller.principal)
            if (key.kind == Kind.KIND_NAMESPACE) {
                dropNamespace(key, request.expectedRevision)
            }
            val mutation = Mutation.Delete(key, request.expectedRevision)
            val applied = apply(key, mutation, request.expectedRevision, caller.principal.id)
            DeleteResponse.newBuilder().setRevision(applied.revision).setDeleted(applied.changed > 0).build()
        }
    }

    suspend fun history(request: HistoryRequest, caller: Caller): HistoryResponse {
        requireValid(validator.validate(request))
        val key = request.key
        requireAllowed(permissionOf(caller.principal, Verb.VERB_GET, key.kind, key.namespace), key.name)
        val before = PageTokens.history(request.pageToken)
        val limit = request.limit.takeIf { it in 1..limits.configHistorySize } ?: limits.configHistorySize
        val remaining = store.history(request.key, limit).filter { it.meta.revision < before }
        val items = remaining.chunkedByBytes(limits.maxMessageBytes) { it.serializedSize }.firstOrNull().orEmpty()
        return HistoryResponse.newBuilder()
            .addAllRevisions(items)
            .setNextPageToken(if (remaining.size > items.size) items.last().meta.revision.toString() else "")
            .build()
    }

    private fun permissionOf(principal: Principal, verb: Verb, kind: Kind, namespace: String): Permission {
        val permission = authorizer.permission(principal, verb, kind, namespace)
        if (permission == Permission.None) {
            throw CoSkyException.permissionDenied(
                "${principal.id} may not ${verb.name.removePrefix("VERB_")} ${kind.name.removePrefix("KIND_")} " +
                    "in namespace $namespace.",
            )
        }
        return permission
    }

    private fun requireAllowed(permission: Permission, name: String) {
        if (!permission.allows(name)) {
            throw CoSkyException.permissionDenied("This does not extend to $name.")
        }
    }

    /**
     * For an Instance both the new and any existing service count, or an account could take over another service's
     * instance. A lease is only for its owner (design §7.8).
     */
    private suspend fun authorizePut(resource: Resource, key: ResourceKey, principal: Principal) {
        val permission = permissionOf(principal, Verb.VERB_PUT, key.kind, key.namespace)
        requireAllowed(permission, resource.authorizedName)
        if (key.kind != Kind.KIND_INSTANCE) {
            return
        }
        viewOf(key.namespace, 0).objects[key]?.let { requireAllowed(permission, it.authorizedName) }
        val lease = resource.meta.lease
        if (lease != 0L && principal != Principal.ANONYMOUS) {
            val owner = store.leaseOwners(key.namespace, listOf(lease))[lease]
            if (owner != null && owner != principal.id) {
                throw CoSkyException.permissionDenied("Lease $lease belongs to another subject.")
            }
        }
    }

    private suspend fun authorizeDelete(key: ResourceKey, principal: Principal) {
        val permission = permissionOf(principal, Verb.VERB_DELETE, key.kind, key.namespace)
        if (key.kind == Kind.KIND_INSTANCE) {
            viewOf(key.namespace, 0).objects[key]?.let { requireAllowed(permission, it.authorizedName) }
        } else {
            requireAllowed(permission, key.name)
        }
    }

    /**
     * Tokens are not for writing: a ServiceAccount keeps the ones it has, whatever the request says (design §7.8). The
     * compare-and-set keeps a token created meanwhile from being lost.
     */
    private suspend fun withStoredTokens(request: PutRequest): PutRequest {
        val resource = request.resource
        if (!resource.hasServiceAccount()) {
            return request
        }
        val stored = viewOf(resource.key.namespace, 0).objects[resource.key]
        val tokens = stored?.serviceAccount?.tokensList.orEmpty()
        return request.toBuilder()
            .setResource(resource.toBuilder().apply { serviceAccountBuilder.clearTokens().addAllTokens(tokens) })
            .setExpectedRevision(
                when {
                    request.expectedRevision != Mutation.NO_CHECK -> request.expectedRevision
                    else -> stored?.meta?.revision ?: Mutation.MUST_NOT_EXIST
                },
            )
            .build()
    }

    /**
     * Records the write and how it ended, in the audit log (design §7.8).
     */
    private inline fun <T> audited(
        caller: Caller,
        verb: Verb,
        key: ResourceKey,
        revisionOf: (T) -> Long,
        block: () -> T,
    ): T {
        val response = try {
            block()
        } catch (e: CoSkyException) {
            record(caller, verb, key, null, e.errorCode.grpcCode.name)
            throw e
        }
        record(caller, verb, key, revisionOf(response), RESULT_OK)
        return response
    }

    private fun record(caller: Caller, verb: Verb, key: ResourceKey, revision: Long?, result: String) {
        audit.record(caller.principal.id, verb, key.kind, key.namespace, key.name, revision, result, caller.remote)
    }

    /**
     * The first two of the three steps of §7.2; the caller deletes the Namespace object last, so that an interrupted
     * deletion leaves a trace for [NamespaceReconciler] to finish.
     */
    private suspend fun dropNamespace(key: ResourceKey, expectedRevision: Long) {
        // Before anything irreversible: the store only checks it on the last step, when the data is already gone.
        checkExpected(key, expectedRevision)
        when (store.terminateNamespace(key.name)) {
            TerminateResult.NOT_EMPTY -> throw CoSkyException.namespaceNotEmpty(key.name)
            TerminateResult.NOT_FOUND -> throw CoSkyException.namespaceNotFound(key.name)
            TerminateResult.TERMINATED -> store.dropNamespace(key.name)
        }
    }

    private suspend fun checkExpected(key: ResourceKey, expectedRevision: Long) {
        if (expectedRevision == Mutation.NO_CHECK) {
            return
        }
        val current = viewOf(key.namespace, 0).objects[key]?.meta?.revision ?: 0
        if (current != expectedRevision) {
            throw CoSkyException.revisionConflict(key, expectedRevision, current)
        }
    }

    /**
     * A view no older than [minRevision].
     */
    private suspend fun viewOf(namespace: String, minRevision: Long): NamespaceView {
        val cache = caches.of(namespace)
        val view = withTimeoutOrNull(minRevisionTimeout) {
            cache.views.first { it != null && it.revision >= minRevision }
        }
        if (view != null && !view.isClosed) {
            return view
        }
        throw when {
            view != null -> CoSkyException.namespaceNotFound(namespace)
            cache.isDiverged -> CoSkyException.revisionRegression(namespace)
            cache.views.value == null -> CoSkyException.storeUnavailable("Namespace $namespace is loading.")
            else -> CoSkyException.minRevisionTimeout(namespace, minRevision)
        }
    }

    private fun matches(resource: Resource, request: ListRequest): Boolean =
        resource.kind == request.kind &&
            (request.service.isEmpty() || resource.instance.service == request.service) &&
            request.labelSelectorMap.all { (label, value) -> resource.meta.labelsMap[label] == value }

    private suspend fun apply(
        key: ResourceKey,
        mutation: Mutation,
        expected: Long,
        actor: String,
    ): ApplyResult.Applied = when (val result = store.apply(key.namespace, listOf(mutation), actor)) {
        is ApplyResult.Applied -> result
        is ApplyResult.Conflict -> throw CoSkyException.revisionConflict(key, expected, result.currentRevision ?: 0)
        is ApplyResult.LeaseNotFound -> throw CoSkyException.leaseNotFound(key.namespace, result.lease)
        ApplyResult.NamespaceNotActive -> throw CoSkyException.namespaceNotFound(key.namespace)
        ApplyResult.BatchTooLarge -> error("A single mutation cannot exceed the batch limit.")
    }

    private companion object {
        const val RESULT_OK = "OK"
    }
}
