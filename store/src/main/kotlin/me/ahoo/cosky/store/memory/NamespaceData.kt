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

package me.ahoo.cosky.store.memory

import com.google.protobuf.Timestamp
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.store.ApplyResult
import me.ahoo.cosky.store.Change
import me.ahoo.cosky.store.ChangeBatch
import me.ahoo.cosky.store.ChangeType
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.NamespaceState
import me.ahoo.cosky.store.Position
import me.ahoo.cosky.store.key
import me.ahoo.cosky.store.withoutReadOnlyMeta
import java.time.Instant
import kotlin.time.Duration

internal data class LeaseRecord(val expiresAt: Instant, val ttl: Duration, val owner: String)

/**
 * One change in the log. [writeId] tells apart revisions with the same number on different timelines, as the metadata
 * in a Redis log entry does.
 */
internal data class LogEntry(val revision: Long, val writeId: Long, val change: Change)

internal data class Settings(val logSize: Int, val historySize: Int, val keepRevisions: Int)

/**
 * What one commit stamps on the objects it writes.
 */
internal data class Write(val id: Long, val now: Instant, val actor: String)

/**
 * The immutable state of a namespace. Every commit produces a new value, so a batch takes effect atomically by
 * swapping the reference once.
 */
internal data class NamespaceData(
    val revision: Long = 0,
    val epoch: String,
    val state: NamespaceState,
    val objects: PersistentMap<ResourceKey, Resource> = persistentHashMapOf(),
    val log: PersistentList<LogEntry> = persistentListOf(),
    val history: PersistentMap<ResourceKey, PersistentList<Resource>> = persistentHashMapOf(),
    val leases: PersistentMap<Long, LeaseRecord> = persistentHashMapOf(),
    /** Instance name to lease: an Instance holds at most one lease. */
    val attachments: PersistentMap<String, Long> = persistentHashMapOf(),
    val leaseSequence: Long = 0,
    val ancestors: PersistentList<NamespaceData> = persistentListOf(),
) {
    val position: Position
        get() = Position(epoch, revision)

    /**
     * The oldest position that can be resumed from.
     */
    val earliestResumable: Long
        get() = log.firstOrNull()?.let { it.revision - 1 } ?: revision

    fun writeIdAt(target: Long): Long? = log.lastOrNull { it.revision == target }?.writeId

    fun applying(mutations: List<Mutation>, write: Write, settings: Settings): Pair<NamespaceData, ApplyResult> {
        if (state != NamespaceState.ACTIVE) {
            return this to ApplyResult.NamespaceNotActive
        }
        mutations.forEachIndexed { index, mutation ->
            rejection(index, mutation, write.now)?.let { return this to it }
        }
        val effective = mutations.filter { changes(it) }
        if (effective.isEmpty()) {
            return this to ApplyResult.Applied(revision, 0)
        }
        val next = commit(settings) { nextRevision ->
            effective.fold(this) { data, mutation ->
                when (mutation) {
                    is Mutation.Put -> data.putting(mutation.resource, nextRevision, write, settings)
                    is Mutation.Delete -> data.deleting(mutation.key, nextRevision, write)
                }
            }
        }
        return next to ApplyResult.Applied(next.revision, effective.size)
    }

    /**
     * Null when [lease] does not exist, or has not expired and [force] is off. A lease without Instances leaves no
     * trace in the log, so it creates no revision.
     */
    fun expiring(lease: Long, force: Boolean, write: Write, settings: Settings): NamespaceData? {
        val record = leases[lease] ?: return null
        if (!force && record.expiresAt.isAfter(write.now)) {
            return null
        }
        val attached = attachments.filterValues { it == lease }.keys
        val cleared = if (attached.isEmpty()) {
            this
        } else {
            commit(settings) { nextRevision ->
                attached.fold(this) { data, name ->
                    data.deleting(instanceKey(name), nextRevision, write)
                }
            }
        }
        return cleared.copy(leases = cleared.leases.removing(lease))
    }

    private fun instanceKey(name: String): ResourceKey =
        objects.keys.first { it.kind == Kind.KIND_INSTANCE && it.name == name }

    private fun rejection(index: Int, mutation: Mutation, now: Instant): ApplyResult? {
        val currentRevision = objects[mutation.key]?.meta?.revision
        val conflict = when (mutation.expectedRevision) {
            Mutation.NO_CHECK -> false
            Mutation.MUST_NOT_EXIST -> currentRevision != null
            else -> currentRevision != mutation.expectedRevision
        }
        if (conflict) {
            return ApplyResult.Conflict(index, currentRevision)
        }
        val lease = (mutation as? Mutation.Put)?.resource?.meta?.lease ?: 0L
        if (lease != 0L && leases[lease]?.expiresAt?.isAfter(now) != true) {
            return ApplyResult.LeaseNotFound(index, lease)
        }
        return null
    }

    private fun changes(mutation: Mutation): Boolean {
        val current = objects[mutation.key]
        return when (mutation) {
            is Mutation.Put ->
                current == null || current.withoutReadOnlyMeta() != mutation.resource.withoutReadOnlyMeta()

            is Mutation.Delete -> current != null
        }
    }

    private inline fun commit(settings: Settings, block: NamespaceData.(Long) -> NamespaceData): NamespaceData {
        val nextRevision = revision + 1
        val changed = copy(revision = nextRevision).block(nextRevision)
        return changed.copy(
            log = trimmed(changed.log, settings.logSize),
            ancestors = keeping(settings.keepRevisions),
        )
    }

    private fun keeping(keepRevisions: Int): PersistentList<NamespaceData> {
        if (keepRevisions == 0) {
            return ancestors
        }
        return (ancestors + copy(ancestors = persistentListOf())).takeLast(keepRevisions).toPersistentList()
    }

    private fun putting(resource: Resource, nextRevision: Long, write: Write, settings: Settings): NamespaceData {
        val key = resource.key
        val stored = resource.stamped(nextRevision, objects[key], write)
        val lease = stored.meta.lease
        return copy(
            objects = objects.putting(key, stored),
            log = log.adding(LogEntry(nextRevision, write.id, Change(ChangeType.PUT, stored))),
            history = when (key.kind) {
                Kind.KIND_CONFIG -> history.putting(key, prepended(history[key], stored, settings.historySize))
                else -> history
            },
            attachments = when {
                key.kind != Kind.KIND_INSTANCE -> attachments
                lease != 0L -> attachments.putting(key.name, lease)
                else -> attachments.removing(key.name)
            },
        )
    }

    private fun deleting(key: ResourceKey, nextRevision: Long, write: Write): NamespaceData {
        val current = objects.getValue(key)
        val last = current.toBuilder().apply {
            metaBuilder
                .setRevision(nextRevision)
                .setUpdatedAt(write.now.toTimestamp())
                .setUpdatedBy(write.actor)
        }.build()
        return copy(
            objects = objects.removing(key),
            log = log.adding(LogEntry(nextRevision, write.id, Change(ChangeType.DELETE, last))),
            attachments = if (key.kind == Kind.KIND_INSTANCE) attachments.removing(key.name) else attachments,
        )
    }
}

/**
 * Follows a timeline from a position, one observed state of the namespace at a time. States may be skipped.
 */
internal class Tail(from: Position) {
    private var applied = from.revision
    private var appliedWriteId: Long? = null

    val cursor: Long
        get() = applied

    /**
     * Whether the state cannot be on the timeline already followed: the revision went back, or the revision already
     * applied now holds a different write.
     */
    fun isDiverged(data: NamespaceData): Boolean {
        if (data.revision < applied) {
            return true
        }
        val current = data.writeIdAt(applied)
        return appliedWriteId != null && current != null && current != appliedWriteId
    }

    fun advance(data: NamespaceData): List<ChangeBatch> {
        if (appliedWriteId == null) {
            appliedWriteId = data.writeIdAt(applied)
        }
        val entries = data.log.takeLastWhile { it.revision > applied }
        entries.lastOrNull()?.let {
            applied = it.revision
            appliedWriteId = it.writeId
        }
        return entries.groupBy { it.revision }
            .map { (revision, group) -> ChangeBatch(revision, group.map { it.change }) }
    }
}

private fun prepended(
    existing: PersistentList<Resource>?,
    stored: Resource,
    historySize: Int,
): PersistentList<Resource> =
    (listOf(stored) + existing.orEmpty()).take(historySize).toPersistentList()

/**
 * Drops the oldest revisions, whole, while over [logSize]; the newest revision always stays.
 */
private fun trimmed(log: PersistentList<LogEntry>, logSize: Int): PersistentList<LogEntry> {
    var current = log
    while (current.size > logSize && current.first().revision != current.last().revision) {
        val oldest = current.first().revision
        current = current.removingAll { it.revision == oldest }
    }
    return current
}

private fun Resource.stamped(nextRevision: Long, current: Resource?, write: Write): Resource = toBuilder().apply {
    val now = write.now.toTimestamp()
    metaBuilder
        .setRevision(nextRevision)
        .setCreateRevision(current?.meta?.createRevision ?: nextRevision)
        .setCreatedAt(current?.meta?.createdAt ?: now)
        .setUpdatedAt(now)
        .setUpdatedBy(write.actor)
}.build()

private fun Instant.toTimestamp(): Timestamp = Timestamp.newBuilder()
    .setSeconds(epochSecond)
    .setNanos(nano)
    .build()
