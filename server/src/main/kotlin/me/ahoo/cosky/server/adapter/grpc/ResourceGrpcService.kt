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

package me.ahoo.cosky.server.adapter.grpc

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.DeleteRequest
import me.ahoo.cosky.api.v1.DeleteResponse
import me.ahoo.cosky.api.v1.GetRequest
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.PutRequest
import me.ahoo.cosky.api.v1.PutResponse
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.ResourceServiceGrpcKt
import me.ahoo.cosky.api.validation.Validator
import me.ahoo.cosky.api.validation.requireValid
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.ApplyResult
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.TerminateResult
import me.ahoo.cosky.store.key
import org.springframework.stereotype.Service
import kotlin.time.Duration.Companion.seconds

/**
 * The minimal resource path of the integration check (roadmap W2c): no authentication, listing or history yet.
 */
@Service
class ResourceGrpcService(
    private val store: Store,
    private val caches: WatchCache,
) : ResourceServiceGrpcKt.ResourceServiceCoroutineImplBase(VirtualThreads.dispatcher) {
    override suspend fun get(request: GetRequest): Resource = grpcCall {
        requireValid(Validator.DEFAULT.validate(request))
        val cache = caches.of(request.key.namespace)
        val view = if (request.minRevision > 0) {
            withTimeout(MIN_REVISION_TIMEOUT) {
                cache.views.first { it != null && it.revision >= request.minRevision }!!
            }
        } else {
            cache.awaitView()
        }
        view.objects[request.key] ?: throw CoSkyException.resourceNotFound(request.key)
    }

    override suspend fun put(request: PutRequest): PutResponse = grpcCall {
        requireValid(Validator.DEFAULT.validate(request))
        val key = request.resource.key
        if (key.kind == Kind.KIND_NAMESPACE) {
            store.createNamespace(key.name)
        }
        val applied = apply(key, Mutation.Put(request.resource, request.expectedRevision), request.expectedRevision)
        PutResponse.newBuilder().setRevision(applied.revision).setChanged(applied.changed > 0).build()
    }

    override suspend fun delete(request: DeleteRequest): DeleteResponse = grpcCall {
        requireValid(Validator.DEFAULT.validate(request))
        val key = request.key
        if (key.kind == Kind.KIND_NAMESPACE) {
            terminate(key.name)
        }
        val applied = apply(key, Mutation.Delete(key, request.expectedRevision), request.expectedRevision)
        if (key.kind == Kind.KIND_NAMESPACE) {
            store.dropNamespace(key.name)
        }
        DeleteResponse.newBuilder().setRevision(applied.revision).setDeleted(applied.changed > 0).build()
    }

    private suspend fun terminate(namespace: String) {
        when (store.terminateNamespace(namespace)) {
            TerminateResult.NOT_EMPTY -> throw CoSkyException.namespaceNotEmpty(namespace)
            TerminateResult.NOT_FOUND -> throw CoSkyException.namespaceNotFound(namespace)
            TerminateResult.TERMINATED -> Unit
        }
    }

    private suspend fun apply(key: ResourceKey, mutation: Mutation, expected: Long): ApplyResult.Applied =
        when (val result = store.apply(key.namespace, listOf(mutation), ACTOR)) {
            is ApplyResult.Applied -> result
            is ApplyResult.Conflict -> throw CoSkyException.revisionConflict(key, expected, result.currentRevision ?: 0)
            is ApplyResult.LeaseNotFound -> throw CoSkyException.leaseNotFound(key.namespace, result.lease)
            ApplyResult.NamespaceNotActive -> throw CoSkyException.namespaceNotFound(key.namespace)
            ApplyResult.BatchTooLarge -> error("A single mutation cannot exceed the batch limit.")
        }

    private companion object {
        /** Authentication comes with W3a; until then every write has the same author. */
        const val ACTOR = "anonymous"
        val MIN_REVISION_TIMEOUT = 3.seconds
    }
}
