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
 * checks in order: a recovery grace, a pause on mass expiry, and a cap per tick.
 */
class LeaseReaper(
    private val store: Store,
    private val namespaces: suspend () -> Collection<String>,
    private val options: Options = Options(),
    private val clock: Clock = Clock.systemUTC(),
    private val owner: String = UUID.randomUUID().toString(),
    private val maintenance: suspend () -> Unit = {},
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
    private var lastTick: Instant? = null
    private var lastMaintenance: Instant? = null
    private val pausedSince = ConcurrentHashMap<String, Instant>()

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
                leaderSince = null
                log.warn("A reaper tick failed.", e)
            }
            delay(options.interval.toKotlinDuration())
        }
    }

    suspend fun tick() {
        val acquired = store.tryLock(LOCK, owner, options.lockTtl.toKotlinDuration())
        val now = clock.instant()
        noteStall(now)
        lastTick = now
        if (!acquired) {
            leaderSince = null
            return
        }
        if (leaderSince == null) {
            leaderSince = now
        }
        maintain(now)
        if (inGrace(now)) {
            return
        }
        namespaces().forEach { reap(it, now) }
    }

    /**
     * What else one server at a time should do. It deletes nothing a live process depends on, so it needs no grace.
     */
    private suspend fun maintain(now: Instant) {
        val last = lastMaintenance
        if (last == null || Duration.between(last, now) >= options.maintenanceInterval) {
            lastMaintenance = now
            maintenance()
        }
    }

    /**
     * A store that hangs instead of failing is an outage too: nothing renewed while the ticks stood still.
     */
    private fun noteStall(now: Instant) {
        val previous = lastTick ?: return
        if (Duration.between(previous, now) > options.interval.multipliedBy(STALL_INTERVALS)) {
            lastFailure = now
        }
    }

    private fun inGrace(now: Instant): Boolean {
        val since = listOfNotNull(leaderSince, lastFailure).max()
        return Duration.between(since, now) < options.grace
    }

    private suspend fun reap(namespace: String, now: Instant) {
        if (isProtected(namespace, now)) {
            return
        }
        store.expiredLeases(namespace, options.maxPerTick).forEach { lease ->
            store.expireLease(namespace, lease, ACTOR, force = false)
        }
    }

    private suspend fun isProtected(namespace: String, now: Instant): Boolean {
        val stats = store.leaseStats(namespace)
        val massive = stats.total >= options.protectionMinLeases &&
            stats.expired.toDouble() / stats.total > options.protectionThreshold
        if (!massive) {
            pausedSince.remove(namespace)
            return false
        }
        val since = pausedSince.computeIfAbsent(namespace) {
            log.error("{} of {} leases expired in namespace {}; pausing the reaper.", stats.expired, stats.total, it)
            now
        }
        return Duration.between(since, now) < options.protectionMaxDuration
    }

    companion object {
        const val LOCK = "reaper"
        const val ACTOR = "system"
        private const val STALL_INTERVALS = 5L
        private val log = LoggerFactory.getLogger(LeaseReaper::class.java)
    }
}
