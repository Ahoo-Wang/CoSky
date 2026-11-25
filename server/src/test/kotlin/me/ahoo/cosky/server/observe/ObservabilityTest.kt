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

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.GoneReason
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.WatchRequest
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.namespaceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.lease.LeaseReaper
import me.ahoo.cosky.server.watch.NamespaceCache
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.server.watch.WatchSession
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.key
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.boot.health.contributor.Status
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ObservabilityTest {
    private val registry = SimpleMeterRegistry()
    private val metrics = CoSkyMetrics(registry)
    private val store = MemoryStore()
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)

    @AfterEach
    fun stop() {
        scope.cancel()
    }

    private fun config(name: String): Resource = resource {
        meta = objectMeta {
            namespace = NAMESPACE
            this.name = name
        }
        config = configSpec {
            format = ConfigFormat.CONFIG_FORMAT_TEXT
            content = "a"
        }
    }

    private fun namespaceObject(name: String): Resource = resource {
        meta = objectMeta {
            namespace = NamingRules.SYSTEM_NAMESPACE
            this.name = name
        }
        namespace = namespaceSpec {}
    }

    private fun scenario(block: suspend CoroutineScope.() -> Unit): Unit = runBlocking(VirtualThreads.dispatcher) {
        withTimeout(10.seconds) {
            store.createNamespace(NAMESPACE)
            block()
        }
    }

    private fun create(fromRevision: Long = 0, fromEpoch: String = ""): WatchRequest.Create =
        WatchRequest.Create.newBuilder()
            .setWatchId(1)
            .setNamespace(NAMESPACE)
            .setKind(Kind.KIND_CONFIG)
            .setFromRevision(fromRevision)
            .setFromEpoch(fromEpoch)
            .build()

    /**
     * @param reason the tag, as design §7.9 documents it.
     */
    private fun goneCount(reason: String): Double =
        registry.counter("cosky_watch_gone_total", "reason", reason).count()

    @Test
    fun `a write counts its time, and the cache reports how far it got`() = scenario {
        val metered = MeteredStore(store, metrics)
        metered.apply(NAMESPACE, listOf(Mutation.Put(config("a"))), "test")
        registry.timer("cosky_store_apply_seconds").count().assert().isEqualTo(1)

        val cache = NamespaceCache(NAMESPACE, store, WINDOW, metrics = metrics)
        val following = launch { cache.run() }
        cache.views.first { it != null }
        metered.apply(NAMESPACE, listOf(Mutation.Put(config("b"))), "test")
        cache.views.first { it?.revision == 2L }
        while (registry.find("cosky_cache_revision").gauge()?.value() != 2.0) {
            delay(10.milliseconds)
        }
        (registry.get("cosky_cache_lag_seconds").gauge().value() >= 0).assert().isTrue()
        following.cancel()
    }

    @Test
    fun `a watch counts while it runs, and what it sends`() = scenario {
        val cache = NamespaceCache(NAMESPACE, store, WINDOW, metrics = metrics)
        val following = launch { cache.run() }
        cache.views.first { it != null }
        val active = registry.get("cosky_watch_active").gauge()
        val watching = launch {
            WatchSession(create(), cache, 1024, 1.seconds, metrics = metrics) {}.run()
        }
        while (active.value() != 1.0) {
            delay(10.milliseconds)
        }
        store.apply(NAMESPACE, listOf(Mutation.Put(config("a"))), "test")
        while (registry.counter("cosky_watch_events_sent_total").count() != 1.0) {
            delay(10.milliseconds)
        }
        watching.cancel()
        watching.join()
        active.value().assert().isEqualTo(0.0)
        following.cancel()
    }

    @Test
    fun `a watch says why it has to start over`() = scenario {
        repeat(WINDOW + 2) { store.apply(NAMESPACE, listOf(Mutation.Put(config("c$it"))), "test") }
        val cache = NamespaceCache(NAMESPACE, store, WINDOW, metrics = metrics)
        val following = launch { cache.run() }
        val view = checkNotNull(cache.views.first { it != null })
        WatchSession(create(1, "another"), cache, 1024, 1.seconds, metrics = metrics) {}.run()
        goneCount("epoch").assert().isEqualTo(1.0)
        WatchSession(create(1, view.epoch), cache, 1024, 1.seconds, metrics = metrics) {}.run()
        goneCount("compacted").assert().isEqualTo(1.0)
        WatchSession(create(view.revision + 5, view.epoch), cache, 1024, 10.milliseconds, metrics = metrics) {}.run()
        goneCount("regression").assert().isEqualTo(1.0)
        following.cancel()
    }

    @Test
    fun `a watch that starts over for a change of permissions is not counted, since the window is not to blame`() {
        metrics.gone(GoneReason.GONE_REASON_PERMISSION)
        metrics.gone(GoneReason.GONE_REASON_UNSPECIFIED)
        registry.find("cosky_watch_gone_total").counters().associate { it.id.getTag("reason") to it.count() }
            .assert().isEqualTo(mapOf("compacted" to 0.0, "slow" to 0.0, "epoch" to 0.0, "regression" to 0.0))
    }

    @Test
    fun `an expired lease counts in its namespace, as does a pause on mass expiry`() = scenario {
        val options = LeaseReaper.Options(grace = Duration.ZERO, protectionMinLeases = 2, protectionThreshold = 0.5)
        val reaper = LeaseReaper(store, { listOf(NAMESPACE) }, options, metrics = metrics)
        store.grantLease(NAMESPACE, 1.milliseconds, "test")
        delay(20.milliseconds)
        reaper.tick()
        registry.get("cosky_lease_expired_total").tag("namespace", NAMESPACE).counter().count()
            .assert().isEqualTo(1.0)

        repeat(2) { store.grantLease(NAMESPACE, 1.milliseconds, "test") }
        delay(20.milliseconds)
        reaper.tick()
        registry.get("cosky_lease_protection_active").gauge().value().assert().isEqualTo(1.0)
        registry.get("cosky_lease_active").gauge().value().assert().isEqualTo(2.0)
    }

    @Test
    fun `a replica that is no longer the reaper's leader stops reporting leases`() = scenario {
        val options = LeaseReaper.Options(grace = Duration.ZERO, protectionMinLeases = 2, protectionThreshold = 0.5)
        val reaper = LeaseReaper(store, { listOf(NAMESPACE) }, options, owner = "first", metrics = metrics)
        repeat(2) { store.grantLease(NAMESPACE, 1.milliseconds, "test") }
        delay(20.milliseconds)
        reaper.tick()
        registry.find("cosky_lease_active").gauge().assert().isNotNull()
        registry.find("cosky_lease_protection_active").gauge().assert().isNotNull()

        store.unlock(LeaseReaper.LOCK, "first")
        store.tryLock(LeaseReaper.LOCK, "second", 60.seconds).assert().isTrue()
        reaper.tick()

        // The new leader reports them now: two replicas reporting would add up to twice as many.
        registry.find("cosky_lease_active").gauge().assert().isNull()
        registry.find("cosky_lease_protection_active").gauge().assert().isNull()
    }

    @Test
    fun `the reaper's leader stops reporting a namespace it no longer looks after`() = scenario {
        var namespaces = listOf(NAMESPACE)
        val reaper = LeaseReaper(store, { namespaces }, LeaseReaper.Options(grace = Duration.ZERO), metrics = metrics)
        store.grantLease(NAMESPACE, 30.seconds, "test")
        reaper.tick()
        registry.get("cosky_lease_active").tag("namespace", NAMESPACE).gauge().value().assert().isEqualTo(1.0)

        namespaces = emptyList()
        reaper.tick()

        registry.find("cosky_lease_active").gauge().assert().isNull()
        registry.find("cosky_lease_protection_active").gauge().assert().isNull()
    }

    @Test
    fun `a namespace that is gone takes its series along`() = scenario {
        val caches = WatchCache(store, WINDOW, scope, metrics = metrics)
        caches.of(NAMESPACE).views.first { it != null }
        store.apply(NAMESPACE, listOf(Mutation.Put(config("a"))), "test")
        store.apply(NAMESPACE, listOf(Mutation.Delete(config("a").key)), "test")
        while (registry.find("cosky_cache_revision").gauge()?.value() != 2.0) {
            delay(10.milliseconds)
        }
        metrics.leases(NAMESPACE, 1)
        metrics.antiEntropyDrift(NAMESPACE)

        val epoch = checkNotNull(store.position(NAMESPACE)).epoch
        store.terminateNamespace(NAMESPACE, epoch)
        store.dropNamespace(NAMESPACE, epoch)

        while (registry.find("cosky_cache_revision").gauge() != null) {
            delay(10.milliseconds)
        }
        registry.find("cosky_cache_lag_seconds").gauge().assert().isNull()
        registry.find("cosky_lease_active").gauge().assert().isNull()
        registry.find("cosky_anti_entropy_drift_total").counter().assert().isNull()
    }

    @Test
    fun `an authentication failure says whether a token was missing, no good, or could not be told`() {
        val invalid = CoSkyException.unauthenticated("A valid token is required.")
        val unknown = CoSkyException.storeUnavailable("Access rules are loading.")
        AuthFailure.of(null, invalid).assert().isEqualTo(AuthFailure.MISSING)
        AuthFailure.of(" ", invalid).assert().isEqualTo(AuthFailure.MISSING)
        AuthFailure.of("cosky_id_secret", invalid).assert().isEqualTo(AuthFailure.INVALID)
        AuthFailure.of("cosky_id_secret", unknown).assert().isEqualTo(AuthFailure.UNAVAILABLE)
        AuthFailure.entries.map { it.tag }.assert().containsExactly("missing", "invalid", "unavailable")

        AuthFailure.entries.forEach { reason ->
            registry.get("cosky_auth_failures_total").tag("reason", reason.tag).counter().count()
                .assert().isEqualTo(0.0)
        }
        metrics.authFailure(AuthFailure.INVALID)
        registry.get("cosky_auth_failures_total").tag("reason", "invalid").counter().count().assert().isEqualTo(1.0)
    }

    @Test
    fun `a replica is ready once every namespace has loaded`() = scenario {
        // On the memory store orders could finish loading before readiness is first asked. Held, it is loading then.
        val loadable = CompletableDeferred<Unit>()
        val held = object : Store by store {
            override fun readAll(namespace: String): Flow<Resource> = flow {
                if (namespace == "orders") {
                    loadable.await()
                }
                emitAll(store.readAll(namespace))
            }
        }
        val caches = WatchCache(held, WINDOW, scope)
        val readiness = ReadinessIndicator(caches)
        store.createNamespace(NamingRules.SYSTEM_NAMESPACE)
        store.createNamespace("orders")
        store.apply(NamingRules.SYSTEM_NAMESPACE, listOf(Mutation.Put(namespaceObject("orders"))), "test")
        // Then orders is all that is left to load.
        caches.of(NamingRules.SYSTEM_NAMESPACE).views.first { it != null }

        val loading = readiness.health()
        loading.status.assert().isEqualTo(Status.OUT_OF_SERVICE)
        loading.details["loading"].assert().isEqualTo(listOf("orders"))
        loadable.complete(Unit)
        while (readiness.health().status != Status.UP) {
            delay(10.milliseconds)
        }
        caches.of("orders").views.value.assert().isNotNull()
    }

    private companion object {
        const val NAMESPACE = "observed"
        const val WINDOW = 4
    }
}
