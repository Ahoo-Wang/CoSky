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

import io.grpc.Status
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.GoneReason
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Why a call was not authenticated (design §7.9). Alerts belong on [MISSING] and [INVALID], which a caller can do
 * something about; [UNAVAILABLE] says the server could not tell.
 */
enum class AuthFailure(val tag: String) {
    /** No token came with the call, or an empty one. */
    MISSING("missing"),

    /**
     * The token is no good: malformed, unknown, revoked or expired, or an ID token that does not pass OIDC's checks.
     */
    INVALID("invalid"),

    /** Whether the token is good could not be told: the access rules are loading, or the provider is out of reach. */
    UNAVAILABLE("unavailable"),
    ;

    companion object {
        /**
         * @param credentials what the call carried as its token.
         * @param error why authenticating it failed.
         */
        fun of(credentials: String?, error: CoSkyException): AuthFailure = when {
            credentials.isNullOrBlank() -> MISSING
            error.errorCode.grpcCode == Status.Code.UNAUTHENTICATED -> INVALID
            else -> UNAVAILABLE
        }
    }
}

/**
 * The meters of design §7.9. They count on hot paths, so each is looked up once.
 *
 * A namespace's series go once the namespace is gone, and those the reaper's leader reports go once this replica is not
 * the leader, or the leader no longer looks after the namespace: a series left behind would report its last value for
 * good.
 */
@Suppress("TooManyFunctions") // One function per meter.
class CoSkyMetrics(private val registry: MeterRegistry = SimpleMeterRegistry()) {
    private val watchesActive = AtomicLong().also { registry.gauge("cosky_watch_active", it) }
    private val eventsSent = registry.counter("cosky_watch_events_sent_total")
    private val regressions = registry.counter("cosky_revision_regression_total")
    private val gone = COUNTED_GONE.associateWith { reason ->
        registry.counter("cosky_watch_gone_total", "reason", reason.name.removePrefix("GONE_REASON_").lowercase())
    }
    private val authFailures =
        AuthFailure.entries.associateWith { registry.counter("cosky_auth_failures_total", "reason", it.tag) }

    private val authEnabled = AtomicLong()

    /** The series of a namespace, by meter name and namespace. */
    private val gauges = ConcurrentHashMap<Pair<String, String>, Series>()
    private val counters = ConcurrentHashMap<Pair<String, String>, Counter>()

    private class Series(val value: AtomicLong, val meter: Meter)

    /**
     * A histogram, so that a percentile can be taken over every replica.
     */
    val applyTimer: Timer = Timer.builder("cosky_store_apply_seconds")
        .serviceLevelObjectives(*APPLY_BUCKETS)
        .register(registry)

    fun watchStarted() {
        watchesActive.incrementAndGet()
    }

    fun watchEnded() {
        watchesActive.decrementAndGet()
    }

    fun eventsSent(count: Int) {
        eventsSent.increment(count.toDouble())
    }

    /**
     * Why a watch had to start over (design §7.9); only the reasons of [COUNTED_GONE] are counted.
     */
    fun gone(reason: GoneReason) {
        gone[reason]?.increment()
    }

    fun revisionRegression() {
        regressions.increment()
    }

    /**
     * @param lagMillis from the write to the cache applying it.
     */
    fun cacheApplied(namespace: String, revision: Long, lagMillis: Long) {
        gauge(CACHE_REVISION, namespace).set(revision)
        gauge(CACHE_LAG, namespace, scale = MILLIS_PER_SECOND).set(lagMillis)
    }

    fun antiEntropyDrift(namespace: String) {
        counter(ANTI_ENTROPY_DRIFT, namespace).increment()
    }

    /**
     * Reported by the reaper's leader only.
     */
    fun leases(namespace: String, active: Int) {
        gauge(LEASES_ACTIVE, namespace).set(active.toLong())
    }

    fun leaseExpired(namespace: String) {
        counter(LEASES_EXPIRED, namespace).increment()
    }

    fun leaseProtection(namespace: String, active: Boolean) {
        gauge(LEASE_PROTECTION, namespace).set(if (active) 1 else 0)
    }

    /**
     * Registered once it is said, so that a server that never says reports nothing rather than authentication off.
     */
    fun authEnabled(enabled: Boolean) {
        authEnabled.set(if (enabled) 1 else 0)
        registry.gauge("cosky_auth_enabled", authEnabled)
    }

    /**
     * This replica is not the reaper's leader, or no longer: what it reported as one is another's to report.
     */
    fun leadershipLost() {
        removeGauges { (name, _) -> name in LEADER_GAUGES }
    }

    /**
     * The namespaces the reaper's leader looks after now. Any other is gone, and has no leases to report.
     */
    fun leasesOf(namespaces: Collection<String>) {
        val kept = namespaces.toSet()
        removeGauges { (name, namespace) -> name in LEADER_GAUGES && namespace !in kept }
    }

    /**
     * The namespace is gone, or at least no longer followed here: its series go with it.
     */
    fun namespaceGone(namespace: String) {
        removeGauges { (_, of) -> of == namespace }
        counters.keys.filter { (_, of) -> of == namespace }.forEach { key ->
            counters.remove(key)?.let { registry.remove(it) }
        }
    }

    fun authFailure(reason: AuthFailure) {
        authFailures.getValue(reason).increment()
    }

    /**
     * A gauge of a namespace, reporting what it holds divided by [scale].
     */
    private fun gauge(name: String, namespace: String, scale: Long = 1): AtomicLong =
        gauges.computeIfAbsent(name to namespace) {
            val value = AtomicLong()
            val meter = Gauge.builder(name, value) { it.get().toDouble() / scale }
                .tag(NAMESPACE, namespace)
                .register(registry)
            Series(value, meter)
        }.value

    private fun removeGauges(which: (Pair<String, String>) -> Boolean) {
        gauges.keys.filter(which).forEach { key ->
            gauges.remove(key)?.let { registry.remove(it.meter) }
        }
    }

    private fun counter(name: String, namespace: String): Counter =
        counters.computeIfAbsent(name to namespace) { registry.counter(name, NAMESPACE, namespace) }

    private companion object {
        const val NAMESPACE = "namespace"
        const val CACHE_REVISION = "cosky_cache_revision"
        const val CACHE_LAG = "cosky_cache_lag_seconds"
        const val ANTI_ENTROPY_DRIFT = "cosky_anti_entropy_drift_total"
        const val LEASES_ACTIVE = "cosky_lease_active"
        const val LEASES_EXPIRED = "cosky_lease_expired_total"
        const val LEASE_PROTECTION = "cosky_lease_protection_active"
        val LEADER_GAUGES = setOf(LEASES_ACTIVE, LEASE_PROTECTION)
        val MILLIS_PER_SECOND = TimeUnit.SECONDS.toMillis(1)

        /** The tags of `cosky_watch_gone_total`: `compacted`, `slow`, `epoch` and `regression`. */
        val COUNTED_GONE = listOf(
            GoneReason.GONE_REASON_COMPACTED,
            GoneReason.GONE_REASON_SLOW,
            GoneReason.GONE_REASON_EPOCH,
            GoneReason.GONE_REASON_REGRESSION,
        )

        /**
         * From a write on one Redis to one waiting out a failover.
         */
        val APPLY_BUCKETS: Array<Duration> = arrayOf(1, 2, 5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000)
            .map { Duration.ofMillis(it.toLong()) }
            .toTypedArray()
    }
}
