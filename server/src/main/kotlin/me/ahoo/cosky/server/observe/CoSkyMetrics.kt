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

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Why a watch had to start over (design §7.9).
 */
enum class GoneReason(val tag: String) {
    /** Its position was older than the window when it connected. */
    COMPACTED("compacted"),

    /** It fell out of the window while following. */
    SLOW("slow"),

    /** Its position came from a timeline the store no longer has. */
    REGRESSION("regression"),

    /** Another epoch began. */
    EPOCH("epoch"),
}

/**
 * The meters of design §7.9. They count on hot paths, so each is looked up once.
 */
@Suppress("TooManyFunctions") // One function per meter.
class CoSkyMetrics(private val registry: MeterRegistry = SimpleMeterRegistry()) {
    private val watchesActive = AtomicLong().also { registry.gauge("cosky_watch_active", it) }
    private val eventsSent = registry.counter("cosky_watch_events_sent_total")
    private val regressions = registry.counter("cosky_revision_regression_total")
    private val leasesExpired = registry.counter("cosky_lease_expired_total")
    private val gone = GoneReason.entries.associateWith { registry.counter("cosky_watch_gone_total", "reason", it.tag) }
    private val gauges = ConcurrentHashMap<Pair<String, String>, AtomicLong>()
    private val counters = ConcurrentHashMap<Pair<String, String>, Counter>()

    val applyTimer: Timer = registry.timer("cosky_store_apply_seconds")

    fun watchStarted() {
        watchesActive.incrementAndGet()
    }

    fun watchEnded() {
        watchesActive.decrementAndGet()
    }

    fun eventsSent(count: Int) {
        eventsSent.increment(count.toDouble())
    }

    fun gone(reason: GoneReason) {
        gone.getValue(reason).increment()
    }

    fun revisionRegression() {
        regressions.increment()
    }

    /**
     * @param lagMillis from the write to the cache applying it.
     */
    fun cacheApplied(namespace: String, revision: Long, lagMillis: Long) {
        gauge("cosky_cache_revision", NAMESPACE, namespace).set(revision)
        gaugeSeconds("cosky_cache_lag_seconds", namespace).set(lagMillis)
    }

    fun antiEntropyDrift(namespace: String) {
        counter("cosky_anti_entropy_drift_total", NAMESPACE, namespace).increment()
    }

    fun leases(namespace: String, active: Int) {
        gauge("cosky_lease_active", NAMESPACE, namespace).set(active.toLong())
    }

    fun leaseExpired() {
        leasesExpired.increment()
    }

    fun leaseProtection(namespace: String, active: Boolean) {
        gauge("cosky_lease_protection_active", NAMESPACE, namespace).set(if (active) 1 else 0)
    }

    fun authFailure(reason: String) {
        counter("cosky_auth_failures_total", "reason", reason).increment()
    }

    private fun gauge(name: String, tag: String, value: String): AtomicLong =
        gauges.computeIfAbsent(name to value) {
            AtomicLong().also { registry.gauge(name, Tags.of(tag, value), it) }
        }

    /**
     * Holds milliseconds and reports seconds, as the name of the meter says.
     */
    private fun gaugeSeconds(name: String, namespace: String): AtomicLong =
        gauges.computeIfAbsent(name to namespace) {
            AtomicLong().also { millis ->
                registry.gauge(name, Tags.of(NAMESPACE, namespace), millis) { current ->
                    current.get().toDouble() / TimeUnit.SECONDS.toMillis(1)
                }
            }
        }

    private fun counter(name: String, tag: String, value: String): Counter =
        counters.computeIfAbsent(name to value) { registry.counter(name, tag, value) }

    private companion object {
        const val NAMESPACE = "namespace"
    }
}
