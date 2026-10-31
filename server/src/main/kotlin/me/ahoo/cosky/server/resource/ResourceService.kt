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
import me.ahoo.cosky.api.validation.Limits
import me.ahoo.cosky.api.validation.Validator
import me.ahoo.cosky.api.validation.requireValid
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
) {
    private val validator = Validator(limits)

    suspend fun get(request: GetRequest): Resource {
        requireValid(validator.validate(request))
        return viewOf(request.key.namespace, request.minRevision).objects[request.key]
            ?: throw CoSkyException.resourceNotFound(request.key)
    }

    suspend fun list(request: ListRequest): ListResponse {
        requireValid(validator.validate(request))
        val token = PageTokens.list(request.pageToken)
        val view = viewOf(request.namespace, request.minRevision)
        if (token != null && token.epoch != view.epoch) {
            throw PageTokens.invalid("The timeline changed since this page was requested; list from the first page.")
        }
        val pageSize = request.pageSize.takeIf { it != 0 } ?: limits.defaultPageSize
        val page = view.objects.values.asSequence()
            .filter { it.meta.name > token?.after.orEmpty() && matches(it, request) }
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

    /**
     * @param actor who is writing, for `updated_by` and the audit trail.
     */
    suspend fun put(request: PutRequest, actor: String): PutResponse {
        requireValid(validator.validate(request))
        val key = request.resource.key
        if (key.kind == Kind.KIND_NAMESPACE) {
            store.createNamespace(key.name)
        }
        val mutation = Mutation.Put(request.resource, request.expectedRevision)
        val applied = apply(key, mutation, request.expectedRevision, actor)
        return PutResponse.newBuilder().setRevision(applied.revision).setChanged(applied.changed > 0).build()
    }

    suspend fun delete(request: DeleteRequest, actor: String): DeleteResponse {
        requireValid(validator.validate(request))
        val key = request.key
        if (key.kind == Kind.KIND_NAMESPACE) {
            dropNamespace(key, request.expectedRevision)
        }
        val applied = apply(key, Mutation.Delete(key, request.expectedRevision), request.expectedRevision, actor)
        return DeleteResponse.newBuilder().setRevision(applied.revision).setDeleted(applied.changed > 0).build()
    }

    suspend fun history(request: HistoryRequest): HistoryResponse {
        requireValid(validator.validate(request))
        val before = PageTokens.history(request.pageToken)
        val limit = request.limit.takeIf { it in 1..limits.configHistorySize } ?: limits.configHistorySize
        val remaining = store.history(request.key, limit).filter { it.meta.revision < before }
        val items = remaining.chunkedByBytes(limits.maxMessageBytes) { it.serializedSize }.firstOrNull().orEmpty()
        return HistoryResponse.newBuilder()
            .addAllRevisions(items)
            .setNextPageToken(if (remaining.size > items.size) items.last().meta.revision.toString() else "")
            .build()
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
}
