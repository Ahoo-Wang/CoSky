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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.v1.ConfigFormat
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

    private fun goneCount(reason: GoneReason): Double =
        registry.counter("cosky_watch_gone_total", "reason", reason.tag).count()

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
        goneCount(GoneReason.EPOCH).assert().isEqualTo(1.0)
        WatchSession(create(1, view.epoch), cache, 1024, 1.seconds, metrics = metrics) {}.run()
        goneCount(GoneReason.COMPACTED).assert().isEqualTo(1.0)
        WatchSession(create(view.revision + 5, view.epoch), cache, 1024, 10.milliseconds, metrics = metrics) {}.run()
        goneCount(GoneReason.REGRESSION).assert().isEqualTo(1.0)
        following.cancel()
    }

    @Test
    fun `an expired lease counts, as does a pause on mass expiry`() = scenario {
        val options = LeaseReaper.Options(grace = Duration.ZERO, protectionMinLeases = 2, protectionThreshold = 0.5)
        val reaper = LeaseReaper(store, { listOf(NAMESPACE) }, options, metrics = metrics)
        store.grantLease(NAMESPACE, 1.milliseconds, "test")
        delay(20.milliseconds)
        reaper.tick()
        registry.counter("cosky_lease_expired_total").count().assert().isEqualTo(1.0)

        repeat(2) { store.grantLease(NAMESPACE, 1.milliseconds, "test") }
        delay(20.milliseconds)
        reaper.tick()
        registry.get("cosky_lease_protection_active").gauge().value().assert().isEqualTo(1.0)
        registry.get("cosky_lease_active").gauge().value().assert().isEqualTo(2.0)
    }

    @Test
    fun `a replica is ready once every namespace has loaded`() = scenario {
        val caches = WatchCache(store, WINDOW, scope)
        val readiness = ReadinessIndicator(caches)
        store.createNamespace(NamingRules.SYSTEM_NAMESPACE)
        store.createNamespace("orders")
        store.apply(NamingRules.SYSTEM_NAMESPACE, listOf(Mutation.Put(namespaceObject("orders"))), "test")
        readiness.health().status.assert().isEqualTo(Status.OUT_OF_SERVICE)
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
