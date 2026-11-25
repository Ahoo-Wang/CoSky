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

package me.ahoo.cosky.server.observe

import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.TopologyEdge
import me.ahoo.cosky.store.ApplyResult
import me.ahoo.cosky.store.ChangeBatch
import me.ahoo.cosky.store.GrantResult
import me.ahoo.cosky.store.LeaseRenewal
import me.ahoo.cosky.store.LeaseStats
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.NamespaceState
import me.ahoo.cosky.store.ObjectVersion
import me.ahoo.cosky.store.Position
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.StoreUnavailableException
import me.ahoo.cosky.store.TerminateResult
import kotlin.time.Duration

/**
 * Times the writes of a store, and counts each operation that could not reach it (design §7.9).
 *
 * A replica stays ready while the store is away, on purpose, so the count is what shows one cut off from it. The
 * reaper of every replica asks for its lock every interval, which keeps counting while nothing else asks. The Redis
 * store's tail of a namespace retries a failed read by itself and fails nothing here.
 */
class MeteredStore(private val delegate: Store, private val metrics: CoSkyMetrics) : Store by delegate {
    override suspend fun apply(namespace: String, mutations: List<Mutation>, actor: String): ApplyResult =
        counted("apply") {
            val sample = Timer.start()
            try {
                delegate.apply(namespace, mutations, actor)
            } finally {
                sample.stop(metrics.applyTimer)
            }
        }

    override suspend fun position(namespace: String): Position? = counted("position") { delegate.position(namespace) }

    override fun readAll(namespace: String): Flow<Resource> = delegate.readAll(namespace).counted("readAll")

    override fun versions(namespace: String): Flow<ObjectVersion> = delegate.versions(namespace).counted("versions")

    override fun changes(namespace: String, from: Position): Flow<ChangeBatch> =
        delegate.changes(namespace, from).counted("changes")

    override suspend fun recentChanges(namespace: String, until: Long, limit: Int, maxBytes: Long): List<ChangeBatch> =
        counted("recentChanges") { delegate.recentChanges(namespace, until, limit, maxBytes) }

    override suspend fun rotateEpoch(namespace: String, expected: String): Position =
        counted("rotateEpoch") { delegate.rotateEpoch(namespace, expected) }

    override suspend fun recordTopology(namespace: String, edges: Collection<TopologyEdge>, retention: Duration) =
        counted("recordTopology") { delegate.recordTopology(namespace, edges, retention) }

    override suspend fun topology(namespace: String): Set<TopologyEdge> =
        counted("topology") { delegate.topology(namespace) }

    override suspend fun history(key: ResourceKey, limit: Int): List<Resource> =
        counted("history") { delegate.history(key, limit) }

    override suspend fun grantLease(namespace: String, ttl: Duration, owner: String): GrantResult =
        counted("grantLease") { delegate.grantLease(namespace, ttl, owner) }

    override suspend fun leaseOwners(namespace: String, leases: Collection<Long>): Map<Long, String?> =
        counted("leaseOwners") { delegate.leaseOwners(namespace, leases) }

    override suspend fun keepAlive(namespace: String, leases: Collection<Long>): Map<Long, LeaseRenewal?> =
        counted("keepAlive") { delegate.keepAlive(namespace, leases) }

    override suspend fun expireLease(namespace: String, lease: Long, actor: String, force: Boolean): Boolean =
        counted("expireLease") { delegate.expireLease(namespace, lease, actor, force) }

    override suspend fun expiredLeases(namespace: String, limit: Int): List<Long> =
        counted("expiredLeases") { delegate.expiredLeases(namespace, limit) }

    override suspend fun leaseStats(namespace: String): LeaseStats =
        counted("leaseStats") { delegate.leaseStats(namespace) }

    override suspend fun createNamespace(namespace: String): Position =
        counted("createNamespace") { delegate.createNamespace(namespace) }

    override suspend fun namespaceState(namespace: String): NamespaceState? =
        counted("namespaceState") { delegate.namespaceState(namespace) }

    override suspend fun terminateNamespace(namespace: String, epoch: String): TerminateResult =
        counted("terminateNamespace") { delegate.terminateNamespace(namespace, epoch) }

    override suspend fun dropNamespace(namespace: String, epoch: String) =
        counted("dropNamespace") { delegate.dropNamespace(namespace, epoch) }

    override suspend fun tryLock(name: String, owner: String, ttl: Duration): Boolean =
        counted("tryLock") { delegate.tryLock(name, owner, ttl) }

    override suspend fun unlock(name: String, owner: String) = counted("unlock") { delegate.unlock(name, owner) }

    /**
     * Only the store out of reach: what else a call fails with, such as a timeline that forked, is no outage.
     */
    private inline fun <T> counted(operation: String, call: () -> T): T = try {
        call()
    } catch (e: StoreUnavailableException) {
        metrics.storeFailure(operation)
        throw e
    }

    private fun <T> Flow<T>.counted(operation: String): Flow<T> = catch { e ->
        if (e is StoreUnavailableException) {
            metrics.storeFailure(operation)
        }
        throw e
    }
}
