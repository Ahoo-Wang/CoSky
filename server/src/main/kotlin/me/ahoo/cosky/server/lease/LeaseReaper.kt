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

package me.ahoo.cosky.server.lease

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.ahoo.cosky.server.observe.CoSkyMetrics
import me.ahoo.cosky.store.Store
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.toKotlinDuration

/**
 * Removes expired leases, while protecting live processes from a control plane outage (design §6.2).
 *
 * After an outage longer than the TTL, the leases that look expired mostly belong to live processes, so each tick
 * checks in order: a recovery grace, a pause on mass expiry, and a cap per tick. The store failing on one namespace
 * gives that namespace a grace of its own, while the others are reaped as usual; failing as a whole, to lock or to list
 * the namespaces, or hanging anywhere, gives every namespace one.
 */
class LeaseReaper(
    private val store: Store,
    private val namespaces: suspend () -> Collection<String>,
    private val options: Options = Options(),
    private val clock: Clock = Clock.systemUTC(),
    private val owner: String = UUID.randomUUID().toString(),
    private val maintenance: suspend () -> Unit = {},
    private val metrics: CoSkyMetrics = CoSkyMetrics(),
) {
    @Suppress("MagicNumber") // The defaults of design §7.6.
    data class Options(
        val interval: Duration = Duration.ofSeconds(1),
        val grace: Duration = Duration.ofSeconds(30),
        val maxPerTick: Int = 100,
        val lockTtl: Duration = Duration.ofSeconds(15),
        val protectionThreshold: Double = 0.5,
        val protectionMinLeases: Int = 10,
        val protectionMaxDuration: Duration = Duration.ofMinutes(5),
        val maintenanceInterval: Duration = Duration.ofSeconds(30),
    )

    private var leaderSince: Instant? = null
    private var lastFailure: Instant? = null
    private var lastTickEnded: Instant? = null
    private var lastMaintenance: Instant? = null
    private val pausedSince = ConcurrentHashMap<String, Instant>()

    /**
     * When the store last failed on a namespace, which then waits out a grace of its own: its processes could not
     * renew either. Only the ticks touch it, one at a time.
     */
    private val failedAt = HashMap<String, Instant>()

    /** Namespaces the store failed on the last time it was asked, so that a run of failures is logged once. */
    private val failing = HashSet<String>()

    /**
     * Namespaces where mass expiry paused reaping, for the metric and alerts.
     */
    val paused: Set<String>
        get() = pausedSince.keys

    suspend fun run() {
        while (currentCoroutineContext().isActive) {
            try {
                tick()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                lastFailure = clock.instant()
                stepDown()
                log.warn("A reaper tick failed.", e)
            }
            delay(options.interval.toKotlinDuration())
        }
    }

    suspend fun tick() {
        try {
            leadOrStepDown()
        } finally {
            lastTickEnded = clock.instant()
        }
    }

    private suspend fun leadOrStepDown() {
        val acquired = store.tryLock(LOCK, owner, options.lockTtl.toKotlinDuration())
        val now = clock.instant()
        noteStall(now)
        if (!acquired) {
            stepDown()
            return
        }
        if (leaderSince == null) {
            leaderSince = now
        }
        maintain(now)
        if (inGrace(now)) {
            return
        }
        val namespaces = namespaces()
        metrics.leasesOf(namespaces)
        // This is also what finds the store having hung during the maintenance.
        if (hung(now)) {
            return
        }
        forgetFailures(namespaces, now)
        namespaces.forEach { namespace ->
            // Finding the store hung while reaping one namespace is a reason not to go on to the next.
            if (inGrace(clock.instant())) {
                return
            }
            reap(namespace, now)
        }
    }

    /**
     * What it knew as leader says nothing later on: an old pause would count against the next mass expiry, whose time
     * limit it would seem to have used up already (design D20). Nor do its lease gauges, which the leader reports.
     */
    private fun stepDown() {
        leaderSince = null
        metrics.leadershipLost()
        pausedSince.clear()
    }

    /**
     * What else one server at a time should do. It deletes nothing a live process depends on, so it needs no grace.
     */
    private suspend fun maintain(now: Instant) {
        val last = lastMaintenance
        if (last == null || Duration.between(last, now) >= options.maintenanceInterval) {
            lastMaintenance = now
            try {
                maintenance()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Reaping does not depend on it, and a failure that came only after hanging is still found as a hang.
                log.warn("Maintenance failed; reaping goes on.", e)
            }
        }
    }

    /**
     * A store that hangs instead of failing is an outage too: nothing renewed while the ticks stood still. Measured
     * from where the previous tick ended, so that a round that takes long, reaping many namespaces, is not one: every
     * call to the store within a tick is timed by itself ([hung]), and what this covers is the time in between, the
     * lock included.
     */
    private fun noteStall(now: Instant) {
        val previous = lastTickEnded ?: return
        if (Duration.between(previous, now) > options.interval.multipliedBy(STALL_INTERVALS)) {
            lastFailure = now
        }
    }

    private fun inGrace(now: Instant): Boolean {
        val since = listOfNotNull(leaderSince, lastFailure).max()
        return Duration.between(since, now) < options.grace
    }

    /**
     * A failure here stays with the namespace, such as one on a Redis Cluster shard that is down: the others are reaped
     * all the same, and the reaper stays leader (design §6.2).
     */
    private suspend fun reap(namespace: String, now: Instant) {
        var started = clock.instant()
        try {
            if (failedRecently(namespace, now)) {
                // Asked all the same, so that its grace runs from its last failure, as the store's runs from its own.
                metrics.leases(namespace, store.leaseStats(namespace).total)
                answered(namespace)
                hung(started)
                return
            }
            if (isProtected(namespace, now)) {
                answered(namespace)
                return
            }
            val expired = store.expiredLeases(namespace, options.maxPerTick)
            answered(namespace)
            if (hung(started)) {
                return
            }
            expired.forEach { lease ->
                started = clock.instant()
                if (store.expireLease(namespace, lease, ACTOR, force = false)) {
                    metrics.leaseExpired(namespace)
                }
                if (hung(started)) {
                    return
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // A call that failed only after hanging is a hang all the same: the store may have hung as a whole, and
            // what the next namespace answers would then be what expired meanwhile.
            hung(started)
            failed(namespace, e)
        }
    }

    private fun failedRecently(namespace: String, now: Instant): Boolean =
        failedAt[namespace]?.let { Duration.between(it, now) < options.grace } == true

    /**
     * Logged once for a run of failures, and counted each time.
     */
    private fun failed(namespace: String, e: Exception) {
        failedAt[namespace] = clock.instant()
        metrics.reaperFailure(namespace)
        if (failing.add(namespace)) {
            log.warn("The store failed on namespace {}; it waits out a grace, and the others go on.", namespace, e)
        } else {
            log.debug("The store failed on namespace {} again.", namespace, e)
        }
    }

    private fun answered(namespace: String) {
        if (failing.remove(namespace)) {
            log.info("The store answers on namespace {} again; it is reaped once its grace is over.", namespace)
        }
    }

    /**
     * Of namespaces whose grace is over, or that are gone.
     */
    private fun forgetFailures(namespaces: Collection<String>, now: Instant) {
        val current = namespaces.toSet()
        failedAt.entries.removeIf { (namespace, at) -> namespace !in current || !failedRecently(namespace, now) }
        failing.retainAll(current)
    }

    /**
     * The store can hang in the middle of a tick as well as between two. What it then answers is what expired during
     * the outage, which is exactly what must not be acted on, so this has to be noticed here too.
     */
    private fun hung(since: Instant): Boolean {
        val now = clock.instant()
        val hung = Duration.between(since, now) > options.interval.multipliedBy(STALL_INTERVALS)
        if (hung) {
            lastFailure = now
        }
        return hung
    }

    private suspend fun isProtected(namespace: String, now: Instant): Boolean {
        val stats = store.leaseStats(namespace)
        metrics.leases(namespace, stats.total)
        val massive = stats.total >= options.protectionMinLeases &&
            stats.expired.toDouble() / stats.total > options.protectionThreshold
        if (!massive) {
            metrics.leaseProtection(namespace, false)
            if (pausedSince.remove(namespace) == null) {
                return false
            }
            // Clients come back one by one, so the share falls below the threshold with many still on their way. They
            // get what any client gets after an outage: reaping now would delete them just as the partition heals.
            lastFailure = now
            return true
        }
        val since = pausedSince.computeIfAbsent(namespace) {
            log.error("{} of {} leases expired in namespace {}; pausing the reaper.", stats.expired, stats.total, it)
            now
        }
        val paused = Duration.between(since, now) < options.protectionMaxDuration
        metrics.leaseProtection(namespace, paused)
        return paused
    }

    companion object {
        const val LOCK = "reaper"
        const val ACTOR = "system"
        private const val STALL_INTERVALS = 5L
        private val log = LoggerFactory.getLogger(LeaseReaper::class.java)
    }
}
