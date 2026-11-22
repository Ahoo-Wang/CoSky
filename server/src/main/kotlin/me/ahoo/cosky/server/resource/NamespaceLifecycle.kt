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
import org.slf4j.LoggerFactory
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
            .setResource(resource.toBuilder().apply { namespaceBuilder.setTerminating(false).clearTerminatingEpoch() })
            .setExpectedRevision(
                when {
                    request.expectedRevision != Mutation.NO_CHECK -> request.expectedRevision
                    else -> stored?.meta?.revision ?: Mutation.MUST_NOT_EXIST
                },
            )
            .build()
    }

    /**
     * Makes the namespace of the Namespace object [key] active, once the object was written, whether for a write or
     * for the reconciler finishing one. The step may come late: should the object have been deleted since, or a
     * deletion of an earlier namespace have got past the mark, the namespace it made again is nobody's, and invisible
     * to the reconciler, which goes by the objects. So the object is read again after the step, which no deletion can
     * pass unseen, and a namespace that turns out to be late is terminated by its own epoch, after which a creation
     * meanwhile that took it for its own makes its own (#1262). One whose epoch was replaced meanwhile, say by
     * anti-entropy, is looked at again under the epoch it has now.
     */
    suspend fun create(key: ResourceKey) {
        do {
            val epoch = store.createNamespace(key.name).epoch
        } while (isLate(key, epoch) && undoLate(key, epoch))
    }

    private suspend fun isLate(key: ResourceKey, epoch: String): Boolean {
        val latest = latest(key) ?: return true
        return latest.namespace.terminating && latest.namespace.terminatingEpoch != epoch
    }

    /**
     * @return whether to create the namespace again, and look once more.
     */
    private suspend fun undoLate(key: ResourceKey, epoch: String): Boolean {
        val name = key.name
        when (store.terminateNamespace(name, epoch)) {
            TerminateResult.TERMINATED -> store.dropNamespace(name, epoch)
            // Another epoch, or no state, since: a creation meanwhile has its object to show, which the next look
            // finds; anything else is late as well.
            TerminateResult.SUPERSEDED, TerminateResult.NOT_FOUND -> return true
            TerminateResult.NOT_EMPTY -> {
                log.error(
                    "Namespace {} was made again by a creation that came after its deletion, and took a write " +
                        "meanwhile: it has no Namespace object, and stays until one is created and deleted.",
                    name,
                )
                return false
            }
        }
        log.info("Namespace {} was deleted before its creation finished: dropped the one it made again.", name)
        return latest(key)?.namespace?.terminating == false
    }

    /**
     * Marks the Namespace object as terminating by compare-and-set, along with the epoch its namespace has, then takes
     * the three steps of §7.2. The mark is the one step that decides between this deletion and a write meanwhile:
     * nothing irreversible happens before it. The epoch keeps every later step to the namespace the mark was for, so
     * that a deletion that comes late leaves one created again since alone.
     */
    suspend fun delete(key: ResourceKey, expectedRevision: Long, actor: String): DeleteResponse {
        val marked = mark(key, expectedRevision, actor)
        val epoch = marked.namespace.terminatingEpoch
        when (store.terminateNamespace(key.name, epoch)) {
            TerminateResult.TERMINATED -> store.dropNamespace(key.name, epoch)
            TerminateResult.NOT_EMPTY -> refuse(marked, actor) { CoSkyException.namespaceNotEmpty(key.name) }
            TerminateResult.NOT_FOUND -> refuse(marked, actor) { CoSkyException.namespaceNotFound(key.name) }
            // Created again, or its timeline replaced, since the mark: not the namespace this deletion was for.
            TerminateResult.SUPERSEDED -> refuse(marked, actor) { current ->
                CoSkyException.revisionConflict(key, marked.meta.revision, current)
            }
        }
        val result = store.apply(key.namespace, listOf(Mutation.Delete(key, marked.meta.revision)), actor)
        val revision = when {
            // Gone already: the reconciler finished a deletion that seemed interrupted, which is as good.
            result is ApplyResult.Conflict && result.currentRevision == null -> marked.meta.revision
            else -> result.requireApplied(key, marked.meta.revision).revision
        }
        return DeleteResponse.newBuilder().setRevision(revision).setDeleted(true).build()
    }

    /**
     * Undoes the mark, then fails with what [refusal] makes of the object's revision now.
     */
    private suspend fun refuse(marked: Resource, actor: String, refusal: (current: Long) -> CoSkyException): Nothing {
        unmark(marked, actor)
        throw refusal(latestViewOf(marked.meta.namespace).objects[marked.key]?.meta?.revision ?: 0)
    }

    /**
     * A deletion stopped after its mark, or one whose namespace turned out to hold something or to be another: the
     * object becomes active again, unless it changed since it was [marked].
     */
    suspend fun unmark(marked: Resource, actor: String = ACTOR) {
        val active = marked.toBuilder().apply { namespaceBuilder.setTerminating(false).clearTerminatingEpoch() }.build()
        store.apply(NamingRules.SYSTEM_NAMESPACE, listOf(Mutation.Put(active, marked.meta.revision)), actor)
    }

    /**
     * The Namespace object as of the store's latest write: a view that is behind may not show a deletion's mark yet.
     */
    suspend fun latest(key: ResourceKey): Resource? = latestViewOf(NamingRules.SYSTEM_NAMESPACE).objects[key]

    /**
     * Drops the data of the namespace the [marked] object was for, then the object.
     */
    suspend fun finish(marked: Resource) {
        store.dropNamespace(marked.meta.name, marked.namespace.terminatingEpoch)
        deleteObject(marked)
    }

    /**
     * Unless it changed since: an object created again in its place is not for this to delete.
     */
    suspend fun deleteObject(namespace: Resource) {
        store.apply(
            NamingRules.SYSTEM_NAMESPACE,
            listOf(Mutation.Delete(namespace.key, namespace.meta.revision)),
            ACTOR,
        )
    }

    /**
     * @return the marked object, with its revision. A deletion already under way is joined rather than refused.
     */
    private suspend fun mark(key: ResourceKey, expectedRevision: Long, actor: String): Resource {
        val stored = latestViewOf(key.namespace).objects[key]
        if (stored?.namespace?.terminating == true) {
            return stored
        }
        // Read after the object: a namespace created again since has changed the object, and the mark fails.
        val epoch = store.position(key.name)?.epoch
        if (stored == null || epoch == null) {
            throw CoSkyException.namespaceNotFound(key.name)
        }
        // What visibly holds something is refused before the mark, so that the usual refusal changes nothing.
        if (caches.viewAt(key.name, 0, minRevisionTimeout).objects.isNotEmpty()) {
            throw CoSkyException.namespaceNotEmpty(key.name)
        }
        val expected = expectedRevision.takeIf { it != Mutation.NO_CHECK } ?: stored.meta.revision
        val terminating = stored.toBuilder()
            .apply { namespaceBuilder.setTerminating(true).setTerminatingEpoch(epoch) }
            .build()
        val revision = store.apply(key.namespace, listOf(Mutation.Put(terminating, expected)), actor)
            .requireApplied(key, expected)
            .revision
        return terminating.toBuilder().apply { metaBuilder.revision = revision }.build()
    }

    private suspend fun latestViewOf(namespace: String): NamespaceView = caches.latestViewAt(
        namespace,
        minRevisionTimeout
    )

    private companion object {
        const val ACTOR = "system"
        val log = LoggerFactory.getLogger(NamespaceLifecycle::class.java)
    }
}
