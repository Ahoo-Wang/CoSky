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

package me.ahoo.cosky.store

import kotlinx.coroutines.flow.Flow
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.TopologyEdge
import kotlin.time.Duration

/**
 * Persistence of the server (design §5.0). Only the server talks to a store.
 *
 * Business failures are typed results; exceptions are for infrastructure failures, misuse, and the two ways a
 * [changes] flow can no longer continue.
 */
interface Store {
    /**
     * Applies [mutations] atomically: either all of them take effect in one new revision, or none. Mutations that
     * change nothing are skipped, and when all are skipped no revision is created.
     *
     * @throws IllegalArgumentException when a key repeats or lies outside [namespace], see [requireWellFormed].
     */
    suspend fun apply(namespace: String, mutations: List<Mutation>, actor: String): ApplyResult

    /**
     * The current position; null unless [namespace] is active.
     */
    suspend fun position(namespace: String): Position?

    /**
     * Every object of [namespace] with its complete read-only metadata.
     */
    fun readAll(namespace: String): Flow<Resource>

    /**
     * Every object's revision alone, for anti-entropy (§6.1).
     */
    fun versions(namespace: String): Flow<ObjectVersion>

    /**
     * Revisions after [from] in order, then new ones as they happen. The flow is cold: cancelling the collection
     * stops the reading. It completes when [namespace] stops being active.
     *
     * A store that finds the timeline forked replaces the epoch before failing the flow (§6.4).
     *
     * @throws RevisionGoneException when [from] fell out of the log window.
     * @throws TimelineDivergedException when [from] is not on the current timeline.
     */
    fun changes(namespace: String, from: Position): Flow<ChangeBatch>

    /**
     * What the log still holds up to revision [until], oldest first: the newest whole revisions, [limit] changes at
     * most. A replica seeds its window with them, so that clients can resume on it right after it loads (§6.1).
     */
    suspend fun recentChanges(namespace: String, until: Long, limit: Int): List<ChangeBatch>

    /**
     * Replaces the epoch if it is still [expected], for a difference found outside a [changes] flow (§6.1). Whoever
     * follows the namespace then has to load it again.
     *
     * @return the position now.
     */
    suspend fun rotateEpoch(namespace: String, expected: String): Position

    /**
     * Refreshes who watches what, to be kept for [retention]. Soft state: it creates no revision and no event, and
     * an edge nobody refreshes goes away (design D30).
     */
    suspend fun recordTopology(namespace: String, edges: Collection<TopologyEdge>, retention: Duration)

    /**
     * The edges that have not expired.
     */
    suspend fun topology(namespace: String): Set<TopologyEdge>

    /**
     * Revisions of a Config, newest first, surviving its deletion; [limit] 0 returns all that are retained.
     */
    suspend fun history(key: ResourceKey, limit: Int): List<Resource>

    suspend fun grantLease(namespace: String, ttl: Duration, owner: String): GrantResult

    /**
     * The subject that granted each lease; null when it no longer exists (§7.8).
     */
    suspend fun leaseOwners(namespace: String, leases: Collection<Long>): Map<Long, String?>

    /**
     * Renews [leases] by their own TTL, including those expired but not reaped yet: expiry only makes a lease
     * reapable (§6.2). The value is the TTL, or null when the lease no longer exists.
     */
    suspend fun keepAlive(namespace: String, leases: Collection<Long>): Map<Long, LeaseRenewal?>

    /**
     * Removes a lease and, in one revision, the Instances attached to it. Unless [force] (Revoke), a lease that has
     * not expired stays.
     *
     * @return whether the lease was removed.
     */
    suspend fun expireLease(namespace: String, lease: Long, actor: String, force: Boolean): Boolean

    /**
     * Up to [limit] expired leases, longest expired first.
     */
    suspend fun expiredLeases(namespace: String, limit: Int): List<Long>

    suspend fun leaseStats(namespace: String): LeaseStats

    /**
     * Makes [namespace] active with a new epoch; no change when it already is. Data still awaiting [dropNamespace] is
     * discarded first.
     */
    suspend fun createNamespace(namespace: String): Position

    /**
     * Null when [namespace] was never created.
     */
    suspend fun namespaceState(namespace: String): NamespaceState?

    /**
     * Atomically checks that [namespace] is empty and marks it terminating, after which writes are rejected (§7.2).
     * Repeating it on a terminating namespace is fine.
     */
    suspend fun terminateNamespace(namespace: String): TerminateResult

    /**
     * Removes all data of a terminating namespace, leaving only its state as a tombstone. Repeating it is fine.
     *
     * @throws IllegalStateException when [namespace] is active.
     */
    suspend fun dropNamespace(namespace: String)

    /**
     * A mutex with an expiry, for electing the reaper. The same [owner] renews it.
     *
     * @return whether [owner] holds the lock now.
     */
    suspend fun tryLock(name: String, owner: String, ttl: Duration): Boolean

    /**
     * Releases the lock if [owner] holds it.
     */
    suspend fun unlock(name: String, owner: String)
}
