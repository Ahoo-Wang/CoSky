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

import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.TopologyEdge
import me.ahoo.cosky.store.ApplyResult
import me.ahoo.cosky.store.ChangeBatch
import me.ahoo.cosky.store.GrantResult
import me.ahoo.cosky.store.Lease
import me.ahoo.cosky.store.LeaseRenewal
import me.ahoo.cosky.store.LeaseStats
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.NamespaceState
import me.ahoo.cosky.store.ObjectVersion
import me.ahoo.cosky.store.Position
import me.ahoo.cosky.store.RevisionGoneException
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.StoreOptions
import me.ahoo.cosky.store.TerminateResult
import me.ahoo.cosky.store.TimelineDivergedException
import me.ahoo.cosky.store.exceeds
import me.ahoo.cosky.store.requireWellFormed
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.toJavaDuration

private const val REAP_AFTER_TTL_PARTS = 3

/**
 * A [Store] in memory, for tests and development mode (`--dev`): nothing survives the process.
 *
 * @param keepRevisions past states to retain for [loseWritesAfter]; tests only, since each is a whole namespace state.
 */
class MemoryStore(
    private val options: StoreOptions = StoreOptions(),
    private val clock: Clock = Clock.systemUTC(),
    keepRevisions: Int = 0,
) : Store {
    private val settings = Settings(options.logSize, options.limits.configHistorySize, keepRevisions)
    private val namespaces = ConcurrentHashMap<String, MutableStateFlow<NamespaceData?>>()
    private val locks = ConcurrentHashMap<String, LockRecord>()
    private val writeIds = AtomicLong()

    override suspend fun apply(namespace: String, mutations: List<Mutation>, actor: String): ApplyResult {
        requireWellFormed(namespace, mutations)
        if (mutations.exceeds(options.limits)) {
            return ApplyResult.BatchTooLarge
        }
        return transition(namespace) { current ->
            if (current == null) {
                null to ApplyResult.NamespaceNotActive
            } else {
                current.applying(mutations, write(actor), settings)
            }
        }
    }

    override suspend fun position(namespace: String): Position? = active(namespace)?.position

    override fun readAll(namespace: String): Flow<Resource> = flow {
        active(namespace)?.objects?.values?.forEach { emit(it) }
    }

    override fun versions(namespace: String): Flow<ObjectVersion> = flow {
        active(namespace)?.objects?.forEach { (key, resource) -> emit(ObjectVersion(key, resource.meta.revision)) }
    }

    override fun changes(namespace: String, from: Position): Flow<ChangeBatch> = flow {
        val tail = Tail(from)
        val batches = slot(namespace).transformWhile { data ->
            if (data?.state != NamespaceState.ACTIVE) {
                return@transformWhile false
            }
            if (data.epoch != from.epoch) {
                throw TimelineDivergedException(data.position)
            }
            if (tail.isDiverged(data)) {
                throw TimelineDivergedException(rotateIfExpected(namespace, from.epoch))
            }
            if (tail.cursor < data.earliestResumable) {
                throw RevisionGoneException(data.earliestResumable)
            }
            tail.advance(data).forEach { emit(it) }
            true
        }
        emitAll(batches)
    }

    override suspend fun recentChanges(namespace: String, until: Long, limit: Int): List<ChangeBatch> =
        active(namespace)?.recentChanges(until, limit).orEmpty()

    override suspend fun history(key: ResourceKey, limit: Int): List<Resource> {
        val revisions = slot(key.namespace).value?.history?.get(key).orEmpty()
        return if (limit == 0) revisions else revisions.take(limit)
    }

    override suspend fun grantLease(namespace: String, ttl: Duration, owner: String): GrantResult =
        transition(namespace) { current ->
            if (current?.state != NamespaceState.ACTIVE) {
                return@transition current to GrantResult.NamespaceNotActive
            }
            val id = current.leaseSequence + 1
            val record = LeaseRecord(now().plus(ttl.toJavaDuration()), ttl, owner)
            current.copy(leaseSequence = id, leases = current.leases.putting(id, record)) to
                GrantResult.Granted(Lease(id, ttl))
        }

    override suspend fun leaseOwners(namespace: String, leases: Collection<Long>): Map<Long, String?> {
        val records = slot(namespace).value?.leases
        return leases.associateWith { records?.get(it)?.owner }
    }

    override suspend fun keepAlive(namespace: String, leases: Collection<Long>): Map<Long, LeaseRenewal?> =
        transition(namespace) { current ->
            if (current == null) {
                return@transition null to leases.associateWith { null }
            }
            val now = now()
            val renewed = leases.fold(current.leases) { records, id ->
                val record = records[id] ?: return@fold records
                records.putting(id, record.copy(expiresAt = now.plus(record.ttl.toJavaDuration())))
            }
            current.copy(leases = renewed) to leases.associateWith { id ->
                renewed[id]?.let { LeaseRenewal(it.ttl, current.attachments.values.count { lease -> lease == id }) }
            }
        }

    override suspend fun expireLease(namespace: String, lease: Long, actor: String, force: Boolean): Boolean =
        transition(namespace) { current ->
            val next = current?.expiring(lease, force, write(actor), settings)
            (next ?: current) to (next != null)
        }

    override suspend fun expiredLeases(namespace: String, limit: Int): List<Long> {
        val now = now()
        return slot(namespace).value?.leases.orEmpty().entries
            .filter { !it.value.expiresAt.plus((it.value.ttl / REAP_AFTER_TTL_PARTS).toJavaDuration()).isAfter(now) }
            .sortedBy { it.value.expiresAt }
            .take(limit)
            .map { it.key }
    }

    override suspend fun leaseStats(namespace: String): LeaseStats {
        val now = now()
        val records = slot(namespace).value?.leases.orEmpty().values
        return LeaseStats(records.size, records.count { !it.expiresAt.isAfter(now) })
    }

    override suspend fun createNamespace(namespace: String): Position = transition(namespace) { current ->
        if (current?.state == NamespaceState.ACTIVE) {
            current to current.position
        } else {
            val created = NamespaceData(epoch = newEpoch(), state = NamespaceState.ACTIVE)
            created to created.position
        }
    }

    override suspend fun namespaceState(namespace: String): NamespaceState? = slot(namespace).value?.state

    override suspend fun terminateNamespace(namespace: String): TerminateResult = transition(namespace) { current ->
        when {
            current == null -> null to TerminateResult.NOT_FOUND
            current.objects.isNotEmpty() -> current to TerminateResult.NOT_EMPTY
            else -> current.copy(state = NamespaceState.TERMINATING) to TerminateResult.TERMINATED
        }
    }

    override suspend fun dropNamespace(namespace: String) {
        transition(namespace) { current ->
            check(current?.state != NamespaceState.ACTIVE) { "Namespace $namespace is still active." }
            val tombstone = current?.let { NamespaceData(epoch = "", state = NamespaceState.TERMINATING) }
            tombstone to Unit
        }
    }

    override suspend fun tryLock(name: String, owner: String, ttl: Duration): Boolean {
        val now = now()
        val record = locks.compute(name) { _, existing ->
            if (existing == null || existing.owner == owner || !existing.expiresAt.isAfter(now)) {
                LockRecord(owner, now.plus(ttl.toJavaDuration()))
            } else {
                existing
            }
        }
        return record?.owner == owner
    }

    override suspend fun unlock(name: String, owner: String) {
        locks.computeIfPresent(name) { _, existing -> existing.takeIf { it.owner != owner } }
    }

    /**
     * Forgets everything after [revision] but keeps the epoch, as a failover to a lagging replica does (§6.4). Tests
     * only; needs `keepRevisions`.
     */
    fun loseWritesAfter(namespace: String, revision: Long) {
        transition(namespace) { current ->
            requireNotNull(current) { "Namespace $namespace does not exist." }
            if (current.revision <= revision) {
                return@transition current to Unit
            }
            val target = current.ancestors.lastOrNull { it.revision <= revision }
            requireNotNull(target) { "Revision $revision of namespace $namespace is not retained." }
            val older = current.ancestors.filter { it.revision < target.revision }.toPersistentList()
            target.copy(ancestors = older) to Unit
        }
    }

    override suspend fun rotateEpoch(namespace: String, expected: String): Position =
        rotateIfExpected(namespace, expected)

    override suspend fun recordTopology(namespace: String, edges: Collection<TopologyEdge>, retention: Duration) {
        transition(namespace) { current ->
            if (current?.state != NamespaceState.ACTIVE) {
                return@transition current to Unit
            }
            val now = now()
            val expiresAt = now.plus(retention.toJavaDuration())
            val refreshed = edges.fold(current.topology) { all, edge -> all.putting(edge, expiresAt) }
            current.copy(topology = refreshed.filterValues { it.isAfter(now) }.toPersistentHashMap()) to Unit
        }
    }

    override suspend fun topology(namespace: String): Set<TopologyEdge> {
        val now = now()
        return active(namespace)?.topology.orEmpty().filterValues { it.isAfter(now) }.keys
    }

    private fun rotateIfExpected(namespace: String, expected: String): Position = transition(namespace) { current ->
        val next = if (current?.epoch == expected) current.copy(epoch = newEpoch()) else current
        next to requireNotNull(next).position
    }

    private fun active(namespace: String): NamespaceData? =
        slot(namespace).value?.takeIf { it.state == NamespaceState.ACTIVE }

    private fun slot(namespace: String): MutableStateFlow<NamespaceData?> =
        namespaces.computeIfAbsent(namespace) { MutableStateFlow(null) }

    private fun now(): Instant = clock.instant().truncatedTo(ChronoUnit.MILLIS)

    private fun write(actor: String): Write = Write(writeIds.incrementAndGet(), now(), actor)

    private fun newEpoch(): String = UUID.randomUUID().toString().replace("-", "")

    /**
     * Swaps the namespace state atomically, computing it again if another writer got in first.
     */
    private inline fun <R> transition(namespace: String, block: (NamespaceData?) -> Pair<NamespaceData?, R>): R {
        val slot = slot(namespace)
        while (true) {
            val current = slot.value
            val (next, result) = block(current)
            if (next === current || slot.compareAndSet(current, next)) {
                return result
            }
        }
    }

    private data class LockRecord(val owner: String, val expiresAt: Instant)
}
