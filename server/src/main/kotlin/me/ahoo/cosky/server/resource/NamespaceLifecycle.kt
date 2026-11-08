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

import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.DeleteResponse
import me.ahoo.cosky.api.v1.PutRequest
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.watch.NamespaceView
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.ApplyResult
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.TerminateResult
import me.ahoo.cosky.store.key
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A Namespace object and the data of its namespace, which lie in different slots (design §7.2, D25, D39). A write to
 * the object compares and sets the revision it read, a deletion marks the object before anything else, and every step
 * after the mark can be taken again, by [NamespaceReconciler] should a deletion be interrupted.
 */
class NamespaceLifecycle(
    private val store: Store,
    private val caches: WatchCache,
    private val minRevisionTimeout: Duration = 3.seconds,
) {
    /**
     * A Namespace being deleted takes no write, and a write to one compares and sets the revision it read: a deletion
     * marks the object first, so whichever comes second fails, and a write never follows a deletion that has begun.
     * Whether it is terminating is the server's to state.
     */
    suspend fun guard(request: PutRequest): PutRequest {
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
     * Marks the Namespace object as terminating by compare-and-set, then takes the three steps of §7.2. The mark is
     * the one step that decides between this deletion and a write meanwhile: nothing irreversible happens before it.
     */
    suspend fun delete(key: ResourceKey, expectedRevision: Long, actor: String): DeleteResponse {
        val marked = mark(key, expectedRevision, actor)
        when (store.terminateNamespace(key.name)) {
            TerminateResult.NOT_EMPTY -> {
                latestViewOf(key.namespace).objects[key]?.let { unmark(it, marked, actor) }
                throw CoSkyException.namespaceNotEmpty(key.name)
            }

            TerminateResult.NOT_FOUND -> {
                latestViewOf(key.namespace).objects[key]?.let { unmark(it, marked, actor) }
                throw CoSkyException.namespaceNotFound(key.name)
            }

            TerminateResult.TERMINATED -> store.dropNamespace(key.name)
        }
        val result = store.apply(key.namespace, listOf(Mutation.Delete(key, marked)), actor)
        val revision = when {
            // Gone already: the reconciler finished a deletion that seemed interrupted, which is as good.
            result is ApplyResult.Conflict && result.currentRevision == null -> marked
            else -> result.requireApplied(key, marked).revision
        }
        return DeleteResponse.newBuilder().setRevision(revision).setDeleted(true).build()
    }

    /**
     * A deletion stopped after its mark, or one whose namespace turned out to hold something: the object becomes
     * active again, unless it changed since [marked].
     */
    suspend fun unmark(namespace: Resource, marked: Long, actor: String = ACTOR) {
        val active = namespace.toBuilder().apply { namespaceBuilder.terminating = false }.build()
        store.apply(NamingRules.SYSTEM_NAMESPACE, listOf(Mutation.Put(active, marked)), actor)
    }

    /**
     * Drops the data of a terminating namespace, then its object.
     */
    suspend fun finish(namespace: Resource) {
        store.dropNamespace(namespace.meta.name)
        deleteObject(namespace)
    }

    suspend fun deleteObject(namespace: Resource) {
        store.apply(NamingRules.SYSTEM_NAMESPACE, listOf(Mutation.Delete(namespace.key)), ACTOR)
    }

    /**
     * @return the revision of the marked object. A deletion already under way is joined rather than refused.
     */
    private suspend fun mark(key: ResourceKey, expectedRevision: Long, actor: String): Long {
        val stored = latestViewOf(key.namespace).objects[key] ?: throw CoSkyException.namespaceNotFound(key.name)
        if (stored.namespace.terminating) {
            return stored.meta.revision
        }
        // What visibly holds something is refused before the mark, so that the usual refusal changes nothing.
        if (caches.viewAt(key.name, 0, minRevisionTimeout).objects.isNotEmpty()) {
            throw CoSkyException.namespaceNotEmpty(key.name)
        }
        val expected = expectedRevision.takeIf { it != Mutation.NO_CHECK } ?: stored.meta.revision
        val terminating = stored.toBuilder().apply { namespaceBuilder.terminating = true }.build()
        return store.apply(key.namespace, listOf(Mutation.Put(terminating, expected)), actor)
            .requireApplied(key, expected)
            .revision
    }

    private suspend fun latestViewOf(namespace: String): NamespaceView = caches.latestViewAt(
        namespace,
        minRevisionTimeout
    )

    private companion object {
        const val ACTOR = "system"
    }
}
