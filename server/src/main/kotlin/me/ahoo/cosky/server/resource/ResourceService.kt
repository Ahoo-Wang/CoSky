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

import me.ahoo.cosky.api.configFormatOf
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
import me.ahoo.cosky.api.v1.ViolationCode
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.validation.Limits
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.api.validation.Validator
import me.ahoo.cosky.api.validation.Violation
import me.ahoo.cosky.api.validation.requireValid
import me.ahoo.cosky.server.audit.AuditLogger
import me.ahoo.cosky.server.auth.Authorizer
import me.ahoo.cosky.server.auth.Caller
import me.ahoo.cosky.server.auth.Permission
import me.ahoo.cosky.server.auth.Principal
import me.ahoo.cosky.server.auth.Tokens
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
        val view = viewOf(request.namespace, request.minRevision).let { reached ->
            // A later page reads no earlier than the first, wherever it lands, or it would leave out what the first
            // page's revision promises (design §4.6). Another timeline is refused below instead of waited for.
            if (token != null && token.epoch == reached.epoch && token.revision > reached.revision) {
                viewOf(request.namespace, token.revision)
            } else {
                reached
            }
        }
        if (token != null && token.epoch != view.epoch) {
            throw PageTokens.invalid("The timeline changed since this page was requested; list from the first page.")
        }
        val pageSize = request.pageSize.takeIf { it != 0 } ?: limits.defaultPageSize
        val candidates = if (request.service.isEmpty()) view.objects.values else view.instancesOf(request.service)
        val page = candidates.asSequence()
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
        return audit.audited(caller, Verb.VERB_PUT, key, PutResponse::getRevision) {
            authorizePut(request, key, caller.principal)
            val write = withStoredTokens(withContentHash(unlessTerminating(request)))
            if (key.kind == Kind.KIND_NAMESPACE) {
                store.createNamespace(key.name)
            }
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
        return audit.audited(caller, Verb.VERB_DELETE, key, DeleteResponse::getRevision) {
            authorizeDelete(key, caller.principal)
            if (key.kind == Kind.KIND_NAMESPACE) {
                return@audited deleteNamespace(key, request.expectedRevision, caller.principal.id)
            }
            val mutation = Mutation.Delete(key, request.expectedRevision)
            val applied = apply(key, mutation, request.expectedRevision, caller.principal.id)
            DeleteResponse.newBuilder().setRevision(applied.revision).setDeleted(applied.changed > 0).build()
        }
    }

    /**
     * Puts the content of an earlier revision again, which also recreates a deleted Config (design §4.5, §7.1).
     *
     * @param expectedRevision as for a Put, of the current object.
     */
    suspend fun rollback(key: ResourceKey, toRevision: Long, expectedRevision: Long, caller: Caller): PutResponse {
        requireValid(validator.validate(key))
        requireAllowed(permissionOf(caller.principal, Verb.VERB_GET, key.kind, key.namespace), key.name)
        val earlier = store.history(key, 0).firstOrNull { it.meta.revision == toRevision }
            ?: throw CoSkyException.resourceNotFound(key)
        val request = PutRequest.newBuilder().setResource(earlier).setExpectedRevision(expectedRevision).build()
        return put(request, caller)
    }

    /**
     * Writes one entry of an archive, which holds nothing but the content: what else a Config has already stays. It is
     * read as of the store's latest write and kept by compare-and-set, so that neither a replica that is behind nor a
     * write meanwhile has its labels or format undone.
     */
    suspend fun importConfig(namespace: String, name: String, content: String, caller: Caller): PutResponse {
        val key = ResourceKey.newBuilder().setNamespace(namespace).setKind(Kind.KIND_CONFIG).setName(name).build()
        val existing = latestViewOf(namespace).objects[key]
        val resource = Resource.newBuilder().apply {
            metaBuilder.setNamespace(namespace).setName(name).putAllLabels(existing?.meta?.labelsMap.orEmpty())
            config = configSpec {
                format = existing?.config?.format ?: configFormatOf(name)
                this.content = content
            }
        }.build()
        val request = PutRequest.newBuilder()
            .setResource(resource)
            .setExpectedRevision(existing?.meta?.revision ?: Mutation.MUST_NOT_EXIST)
            .build()
        return put(request, caller)
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
     * instance, and only after that does it matter that the service cannot change (design §7.1). Attaching one to a
     * lease is only for the lease's owner (design §7.8); keeping it on the lease it is on is not attaching, or an
     * operator could not drain an instance an application registered.
     */
    private suspend fun authorizePut(request: PutRequest, key: ResourceKey, principal: Principal) {
        val resource = request.resource
        val permission = permissionOf(principal, Verb.VERB_PUT, key.kind, key.namespace)
        requireAllowed(permission, resource.authorizedName)
        if (key.kind != Kind.KIND_INSTANCE) {
            return
        }
        val existing = viewOf(key.namespace, request.expectedRevision.coerceAtLeast(0)).objects[key]
        existing?.let { requireAllowed(permission, it.authorizedName) }
        if (existing != null && existing.instance.service != resource.instance.service) {
            throw CoSkyException.invalidArgument(
                listOf(
                    Violation(
                        "resource.instance.service",
                        ViolationCode.VIOLATION_CODE_NOT_ALLOWED,
                        "An Instance stays in its service: delete it, then create it again.",
                    ),
                ),
            )
        }
        val lease = resource.meta.lease
        if (lease != 0L && lease != existing?.meta?.lease && principal != Principal.ANONYMOUS) {
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
     * The hash of a Config is the server's to state, whatever the request says (design §7.1).
     */
    private fun withContentHash(request: PutRequest): PutRequest {
        val resource = request.resource
        if (!resource.hasConfig()) {
            return request
        }
        val sha256 = Tokens.sha256Hex(resource.config.content)
        return request.toBuilder()
            .setResource(resource.toBuilder().apply { configBuilder.setSha256(sha256) })
            .build()
    }

    /**
     * Tokens are not for writing: a ServiceAccount keeps the ones it has, whatever the request says (design §7.8). The
     * compare-and-set keeps a token created meanwhile from being lost, and reading at the revision it expects keeps a
     * replica that is behind from bringing back a token revoked since.
     */
    private suspend fun withStoredTokens(request: PutRequest): PutRequest {
        val resource = request.resource
        if (!resource.hasServiceAccount()) {
            return request
        }
        val stored = viewOf(resource.key.namespace, request.expectedRevision.coerceAtLeast(0)).objects[resource.key]
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
     * A Namespace being deleted takes no write, and a write to one compares and sets the revision it read: a deletion
     * marks the object first, so whichever comes second fails, and a write never follows a deletion that has begun
     * (design §7.2). Whether it is terminating is the server's to state.
     */
    private suspend fun unlessTerminating(request: PutRequest): PutRequest {
        val resource = request.resource
        if (!resource.hasNamespace()) {
            return request
        }
        val stored = latestViewOf(NamingRules.SYSTEM_NAMESPACE).objects[resource.key]
        if (stored?.namespace?.terminating == true) {
            throw CoSkyException.namespaceNotFound(resource.meta.name)
        }
        return request.toBuilder()
            .setResource(resource.toBuilder().apply { namespaceBuilder.terminating = false })
            .setExpectedRevision(
                when {
                    request.expectedRevision != Mutation.NO_CHECK -> request.expectedRevision
                    else -> stored?.meta?.revision ?: Mutation.MUST_NOT_EXIST
                },
            )
            .build()
    }

    /**
     * Marks the Namespace object as terminating by compare-and-set, then takes the three steps of §7.2. The object and
     * the namespace's data lie in different slots, so the mark is the one step that decides between this deletion and
     * a write meanwhile: nothing irreversible happens before it. Every step after it can be taken again, by
     * [NamespaceReconciler] should this request be interrupted.
     */
    private suspend fun deleteNamespace(key: ResourceKey, expectedRevision: Long, actor: String): DeleteResponse {
        val marked = markTerminating(key, expectedRevision, actor)
        when (store.terminateNamespace(key.name)) {
            TerminateResult.NOT_EMPTY -> {
                unmark(key, marked, actor)
                throw CoSkyException.namespaceNotEmpty(key.name)
            }

            TerminateResult.NOT_FOUND -> {
                unmark(key, marked, actor)
                throw CoSkyException.namespaceNotFound(key.name)
            }

            TerminateResult.TERMINATED -> store.dropNamespace(key.name)
        }
        val result = store.apply(key.namespace, listOf(Mutation.Delete(key, marked)), actor)
        val revision = when {
            result is ApplyResult.Applied -> result.revision
            // Gone already: the reconciler finished a deletion that seemed interrupted, which is as good.
            result is ApplyResult.Conflict && result.currentRevision == null -> marked
            else -> apply(key, Mutation.Delete(key, marked), marked, actor).revision
        }
        return DeleteResponse.newBuilder().setRevision(revision).setDeleted(true).build()
    }

    /**
     * @return the revision of the marked object. A deletion already under way is joined rather than refused.
     */
    private suspend fun markTerminating(key: ResourceKey, expectedRevision: Long, actor: String): Long {
        val stored = latestViewOf(key.namespace).objects[key] ?: throw CoSkyException.namespaceNotFound(key.name)
        if (stored.namespace.terminating) {
            return stored.meta.revision
        }
        // What visibly holds something is refused before the mark, so that the usual refusal changes nothing.
        if (viewOf(key.name, 0).objects.isNotEmpty()) {
            throw CoSkyException.namespaceNotEmpty(key.name)
        }
        val expected = expectedRevision.takeIf { it != Mutation.NO_CHECK } ?: stored.meta.revision
        val terminating = stored.toBuilder().apply { namespaceBuilder.terminating = true }.build()
        return apply(key, Mutation.Put(terminating, expected), expected, actor).revision
    }

    private suspend fun unmark(key: ResourceKey, marked: Long, actor: String) {
        val stored = latestViewOf(key.namespace).objects[key] ?: return
        val active = stored.toBuilder().apply { namespaceBuilder.terminating = false }.build()
        store.apply(key.namespace, listOf(Mutation.Put(active, marked)), actor)
    }

    private suspend fun viewOf(namespace: String, minRevision: Long): NamespaceView =
        caches.viewAt(namespace, minRevision, minRevisionTimeout)

    /**
     * What this replica holds once it caught up with the store, for a write that decides on what is there.
     */
    private suspend fun latestViewOf(namespace: String): NamespaceView =
        viewOf(namespace, store.position(namespace)?.revision ?: 0)

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
}
