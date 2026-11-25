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

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.api.v1.endpoint
import me.ahoo.cosky.api.v1.instanceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.store.GrantResult
import me.ahoo.cosky.store.LeaseStats
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.StoreUnavailableException
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

class LeaseReaperTest {
    private val clock = MutableClock()
    private val store = MemoryStore(clock = clock)
    private val options = LeaseReaper.Options(
        grace = Duration.ofSeconds(30),
        maxPerTick = 2,
        protectionThreshold = 0.5,
        protectionMinLeases = 4,
        protectionMaxDuration = Duration.ofMinutes(5),
    )
    private val reaper = LeaseReaper(store, { listOf(NAMESPACE) }, options, clock)

    private suspend fun register(name: String, ttlSeconds: Long = TTL, namespace: String = NAMESPACE): Long {
        val lease = (store.grantLease(namespace, ttlSeconds.seconds, "test") as GrantResult.Granted).lease.id
        store.apply(namespace, listOf(Mutation.Put(instance(name, lease, namespace))), "test")
        return lease
    }

    private fun instance(name: String, lease: Long, namespace: String) = resource {
        meta = objectMeta {
            this.namespace = namespace
            this.name = name
            this.lease = lease
        }
        instance = instanceSpec {
            service = "order"
            endpoint = endpoint {
                scheme = "http"
                host = "10.0.0.1"
                port = 8080
            }
        }
    }

    private suspend fun instances(namespace: String = NAMESPACE): List<String> =
        store.readAll(namespace).toList().map { it.meta.name }

    private suspend fun tickEverySecond(seconds: Long) {
        repeat(seconds.toInt()) {
            reaper.tick()
            clock.advance(Duration.ofSeconds(1))
        }
    }

    private fun scenario(block: suspend () -> Unit): Unit = runBlocking {
        store.createNamespace(NAMESPACE)
        block()
    }

    @Test
    fun `nothing is reaped during the grace after becoming leader`() = scenario {
        register("a")
        clock.advance(Duration.ofSeconds(TTL + 1))
        tickEverySecond(29)
        instances().assert().containsExactly("a")
    }

    @Test
    fun `a dead instance goes once the grace is over`() = scenario {
        register("a")
        clock.advance(Duration.ofSeconds(TTL + 1))
        tickEverySecond(31)
        instances().assert().isEmpty()
    }

    @Test
    fun `a process that renews during the grace keeps its instances`() = scenario {
        val lease = register("a")
        clock.advance(Duration.ofSeconds(TTL + 1))
        tickEverySecond(10)
        store.keepAlive(NAMESPACE, listOf(lease))
        tickEverySecond(25)
        instances().assert().containsExactly("a")
    }

    @Test
    fun `ticks standing still count as an outage and start the grace again`() = scenario {
        tickEverySecond(31)
        val lease = register("a")
        // The store hung: no tick ran for longer than the TTL, and nothing could renew.
        clock.advance(Duration.ofSeconds(TTL + 10))
        reaper.tick()
        instances().assert().containsExactly("a")
        store.keepAlive(NAMESPACE, listOf(lease))
        tickEverySecond(5)
        instances().assert().containsExactly("a")
    }

    @Test
    fun `a round that takes long, as with many namespaces, is no outage`() = scenario {
        val namespaces = (1..12).map { "busy-$it" }
        namespaces.forEach { store.createNamespace(it) }
        // Each namespace answers well within the time a hang takes, and all of them together take longer.
        val busy = object : Store by store {
            override suspend fun leaseStats(namespace: String): LeaseStats {
                if (namespace != NAMESPACE) clock.advance(Duration.ofMillis(500))
                return store.leaseStats(namespace)
            }
        }
        val reaper = LeaseReaper(busy, { namespaces + NAMESPACE }, options, clock)
        repeat(31) {
            reaper.tick()
            clock.advance(Duration.ofSeconds(1))
        }
        register("a", ttlSeconds = 2)
        repeat(4) {
            reaper.tick()
            clock.advance(Duration.ofSeconds(1))
        }
        instances().assert().isEmpty()
    }

    /**
     * The worst moment for a store to hang: when it answers, what it says has expired only did during the outage.
     */
    private suspend fun midTickHang(hangingReaper: (hang: () -> Unit) -> LeaseReaper) {
        var hanging = false
        val reaper = hangingReaper {
            if (hanging) {
                clock.advance(Duration.ofSeconds(TTL + 10))
                hanging = false
            }
        }
        repeat(31) {
            reaper.tick()
            clock.advance(Duration.ofSeconds(1))
        }
        val lease = register("a")
        hanging = true
        reaper.tick()
        instances().assert().containsExactly("a")

        // That started the grace again, which is what lets the process renew in time.
        repeat(5) {
            clock.advance(Duration.ofSeconds(1))
            reaper.tick()
        }
        instances().assert().containsExactly("a")
        store.keepAlive(NAMESPACE, listOf(lease))
    }

    private fun reaperOn(store: Store) = LeaseReaper(store, { listOf(NAMESPACE) }, options, clock)

    @Test
    fun `a store hanging while it lists what expired does not lead to reaping`() = scenario {
        midTickHang { hang ->
            reaperOn(
                object : Store by store {
                    override suspend fun expiredLeases(namespace: String, limit: Int): List<Long> {
                        hang()
                        return store.expiredLeases(namespace, limit)
                    }
                },
            )
        }
    }

    @Test
    fun `a store hanging while it counts leases does not lead to reaping`() = scenario {
        midTickHang { hang ->
            reaperOn(
                object : Store by store {
                    override suspend fun leaseStats(namespace: String): LeaseStats {
                        hang()
                        return store.leaseStats(namespace)
                    }
                },
            )
        }
    }

    @Test
    fun `maintenance hanging does not lead to reaping`() = scenario {
        midTickHang { hang ->
            LeaseReaper(
                store,
                { listOf(NAMESPACE) },
                options.copy(maintenanceInterval = Duration.ZERO),
                clock,
                maintenance = { hang() },
            )
        }
    }

    @Test
    fun `listing the namespaces hanging does not lead to reaping`() = scenario {
        midTickHang { hang ->
            LeaseReaper(
                store,
                {
                    hang()
                    listOf(NAMESPACE)
                },
                options,
                clock,
            )
        }
    }

    @Test
    fun `a tick that keeps its rhythm reaps at once`() = scenario {
        tickEverySecond(31)
        register("a", ttlSeconds = 2)
        tickEverySecond(4)
        instances().assert().isEmpty()
    }

    @Test
    fun `mass expiry pauses the reaper`() = scenario {
        tickEverySecond(31)
        repeat(4) { register("i$it", ttlSeconds = 2) }
        tickEverySecond(10)
        instances().size.assert().isEqualTo(4)
        reaper.paused.assert().containsExactly(NAMESPACE)
    }

    @Test
    fun `a pause does not outlive leadership, so the next mass expiry pauses anew`() = scenario {
        tickEverySecond(31)
        repeat(4) { register("i$it", ttlSeconds = 2) }
        tickEverySecond(10)
        reaper.paused.assert().containsExactly(NAMESPACE)

        clock.advance(options.lockTtl.plusSeconds(1))
        store.tryLock(LeaseReaper.LOCK, "another", options.lockTtl.toKotlinDuration()).assert().isTrue()
        reaper.tick()
        reaper.paused.assert().isEmpty()

        // Longer than a pause lasts: one kept from before would count as used up, and the reaper would go ahead.
        clock.advance(options.protectionMaxDuration)
        store.unlock(LeaseReaper.LOCK, "another")
        tickEverySecond(31)
        instances().size.assert().isEqualTo(4)
        reaper.paused.assert().containsExactly(NAMESPACE)
    }

    @Test
    fun `expiry below the threshold is not mass expiry`() = scenario {
        tickEverySecond(31)
        register("short", ttlSeconds = 2)
        repeat(3) { register("long$it", ttlSeconds = 600) }
        tickEverySecond(4)
        instances().assert().containsExactlyInAnyOrder("long0", "long1", "long2")
        reaper.paused.assert().isEmpty()
    }

    @Test
    fun `an expired lease waits a third of its ttl, long enough to see what expired with it`() = scenario {
        tickEverySecond(31)
        // They renewed at different moments, as clients do, so a partition makes them expire one after the other.
        val leases = (0 until 4).map { register("i$it", ttlSeconds = 30) }
        repeat(4) { index ->
            clock.advance(Duration.ofSeconds(2))
            store.keepAlive(NAMESPACE, leases.drop(index))
        }
        tickEverySecond(31)
        // The first has expired for over two thirds of what it waits, and all of them have expired: nothing goes.
        instances().size.assert().isEqualTo(4)
        tickEverySecond(30)
        reaper.paused.assert().contains(NAMESPACE)
        instances().size.assert().isEqualTo(4)
    }

    @Test
    fun `few leases never count as mass expiry`() = scenario {
        tickEverySecond(31)
        repeat(3) { register("i$it", ttlSeconds = 2) }
        tickEverySecond(5)
        instances().assert().isEmpty()
    }

    @Test
    fun `the pause ends when processes come back`() = scenario {
        tickEverySecond(31)
        val leases = List(4) { register("i$it", ttlSeconds = 2) }
        tickEverySecond(10)
        reaper.paused.assert().containsExactly(NAMESPACE)
        store.keepAlive(NAMESPACE, leases)
        reaper.tick()
        reaper.paused.assert().isEmpty()
        instances().size.assert().isEqualTo(4)
    }

    @Test
    fun `when a pause ends, clients still on their way back get the grace`() = scenario {
        tickEverySecond(31)
        val leases = List(4) { register("i$it", ttlSeconds = 2) }
        tickEverySecond(10)
        reaper.paused.assert().containsExactly(NAMESPACE)

        // Most have come back, which ends the pause, and the last is still reconnecting.
        store.keepAlive(NAMESPACE, leases.take(3))
        tickEverySecond(1)
        reaper.paused.assert().isEmpty()
        tickEverySecond(1)
        store.keepAlive(NAMESPACE, leases.take(3))
        instances().size.assert().isEqualTo(4)

        store.keepAlive(NAMESPACE, leases)
        tickEverySecond(1)
        instances().size.assert().isEqualTo(4)
    }

    @Test
    fun `a client that does not come back after a pause goes once the grace is over`() = scenario {
        tickEverySecond(31)
        val leases = List(4) { register("i$it", ttlSeconds = 60) }
        tickEverySecond(85)
        reaper.paused.assert().containsExactly(NAMESPACE)
        store.keepAlive(NAMESPACE, leases.take(3))
        tickEverySecond(1)
        reaper.paused.assert().isEmpty()
        tickEverySecond(29)
        instances().size.assert().isEqualTo(4)
        tickEverySecond(3)
        instances().size.assert().isEqualTo(3)
    }

    @Test
    fun `a store hanging on one namespace stops the tick before the next`() = scenario {
        var hanging = false
        val first = "first"
        store.createNamespace(first)
        val hangingStore = object : Store by store {
            override suspend fun leaseStats(namespace: String): LeaseStats {
                if (hanging && namespace == first) {
                    clock.advance(Duration.ofSeconds(TTL + 20))
                    hanging = false
                }
                return store.leaseStats(namespace)
            }
        }
        val reaper = LeaseReaper(hangingStore, { listOf(first, NAMESPACE) }, options, clock)
        repeat(31) {
            reaper.tick()
            clock.advance(Duration.ofSeconds(1))
        }
        register("a")
        hanging = true
        reaper.tick()
        instances().assert().containsExactly("a")
    }

    /**
     * The store fails on [broken] while [failing] says so, in [method] only, as one does whose Redis Cluster shard is
     * down while the others answer.
     */
    private fun shardDown(broken: String, method: String, failing: () -> Boolean): Store = object : Store by store {
        override suspend fun leaseStats(namespace: String): LeaseStats {
            failIfDown(namespace, "leaseStats")
            return store.leaseStats(namespace)
        }

        override suspend fun expiredLeases(namespace: String, limit: Int): List<Long> {
            failIfDown(namespace, "expiredLeases")
            return store.expiredLeases(namespace, limit)
        }

        private fun failIfDown(namespace: String, called: String) {
            if (namespace == broken && called == method && failing()) {
                throw StoreUnavailableException("Redis: the shard of $namespace is down.")
            }
        }
    }

    private suspend fun othersReapedWhileOneFails(method: String) {
        val broken = "broken"
        store.createNamespace(broken)
        var failing = false
        // The broken namespace comes first, so that the others are reaped after it failed, in the same tick.
        val reaper = LeaseReaper(shardDown(broken, method) { failing }, { listOf(broken, NAMESPACE) }, options, clock)
        repeat(31) {
            reaper.tick()
            clock.advance(Duration.ofSeconds(1))
        }
        register("a", ttlSeconds = 2)
        clock.advance(Duration.ofSeconds(3))
        failing = true
        reaper.tick()
        instances().assert().isEmpty()

        // Still the leader, with no grace begun again: what expires next goes as soon.
        register("b", ttlSeconds = 2)
        clock.advance(Duration.ofSeconds(3))
        reaper.tick()
        instances().assert().isEmpty()
    }

    @Test
    fun `a namespace the store cannot count the leases of leaves the others to be reaped`() = scenario {
        othersReapedWhileOneFails("leaseStats")
    }

    @Test
    fun `a namespace the store cannot list the expired leases of leaves the others to be reaped`() = scenario {
        othersReapedWhileOneFails("expiredLeases")
    }

    @Test
    fun `a namespace the store failed on waits out a grace of its own, from its last failure`() = scenario {
        val healthy = "healthy"
        store.createNamespace(healthy)
        var failing = false
        val reaper = LeaseReaper(
            shardDown(NAMESPACE, "leaseStats") { failing },
            { listOf(NAMESPACE, healthy) },
            options,
            clock,
        )
        repeat(31) {
            reaper.tick()
            clock.advance(Duration.ofSeconds(1))
        }
        val lease = register("a")
        failing = true
        // Down for longer than the TTL, during which its process could not renew, while the other one is reaped.
        repeat(45) {
            if (it == 10) register("dead", ttlSeconds = 2, namespace = healthy)
            reaper.tick()
            clock.advance(Duration.ofSeconds(1))
        }
        instances(healthy).assert().isEmpty()
        failing = false

        // Its lease expired long enough ago to be reaped, but the grace runs from the last failure.
        repeat(20) {
            reaper.tick()
            clock.advance(Duration.ofSeconds(1))
        }
        instances().assert().containsExactly("a")
        store.keepAlive(NAMESPACE, listOf(lease))
        repeat(15) {
            reaper.tick()
            clock.advance(Duration.ofSeconds(1))
        }
        instances().assert().containsExactly("a")

        // Once the grace is over, one that did not come back goes.
        register("gone", ttlSeconds = 2)
        clock.advance(Duration.ofSeconds(3))
        reaper.tick()
        instances().assert().containsExactly("a")
    }

    @Test
    fun `a store that fails on one namespace only after hanging gives every namespace the grace`() = scenario {
        var hanging = false
        val first = "first"
        store.createNamespace(first)
        val hangingStore = object : Store by store {
            override suspend fun leaseStats(namespace: String): LeaseStats {
                if (hanging && namespace == first) {
                    clock.advance(Duration.ofSeconds(TTL + 20))
                    hanging = false
                    throw StoreUnavailableException("Redis: the command timed out.")
                }
                return store.leaseStats(namespace)
            }
        }
        val reaper = LeaseReaper(hangingStore, { listOf(first, NAMESPACE) }, options, clock)
        repeat(31) {
            reaper.tick()
            clock.advance(Duration.ofSeconds(1))
        }
        val lease = register("a")
        hanging = true
        reaper.tick()
        instances().assert().containsExactly("a")

        repeat(5) {
            clock.advance(Duration.ofSeconds(1))
            reaper.tick()
        }
        instances().assert().containsExactly("a")
        store.keepAlive(NAMESPACE, listOf(lease))
    }

    @Test
    fun `maintenance that fails does not hold up reaping`() = scenario {
        val reaper = LeaseReaper(
            store,
            { listOf(NAMESPACE) },
            options.copy(maintenanceInterval = Duration.ZERO),
            clock,
            maintenance = { throw StoreUnavailableException("Redis: the shard of the system namespace is down.") },
        )
        repeat(31) {
            reaper.tick()
            clock.advance(Duration.ofSeconds(1))
        }
        register("a", ttlSeconds = 2)
        repeat(4) {
            reaper.tick()
            clock.advance(Duration.ofSeconds(1))
        }
        instances().assert().isEmpty()
    }

    @Test
    fun `maintenance failing after hanging does not lead to reaping`() = scenario {
        midTickHang { hang ->
            LeaseReaper(
                store,
                { listOf(NAMESPACE) },
                options.copy(maintenanceInterval = Duration.ZERO),
                clock,
                maintenance = {
                    val before = clock.instant()
                    hang()
                    if (clock.instant() != before) {
                        throw StoreUnavailableException("Redis: the command timed out.")
                    }
                },
            )
        }
    }

    @Test
    fun `the pause has a limit, then reaping resumes at the capped rate`() = scenario {
        tickEverySecond(31)
        repeat(6) { register("i$it", ttlSeconds = 2) }
        clock.advance(Duration.ofSeconds(3))
        reaper.tick()
        clock.advance(options.protectionMaxDuration)
        // Ticks stood still here on purpose: skip the grace that starts again, then watch the cap.
        tickEverySecond(30)
        reaper.tick()
        instances().size.assert().isEqualTo(4)
        clock.advance(Duration.ofSeconds(1))
        reaper.tick()
        instances().size.assert().isEqualTo(2)
    }

    @Test
    fun `only the leader reaps`() = scenario {
        store.tryLock(LeaseReaper.LOCK, "another server", 3600.seconds)
        register("a", ttlSeconds = 2)
        tickEverySecond(40)
        instances().assert().containsExactly("a")
    }

    private class MutableClock : Clock() {
        private var now: Instant = Instant.parse("2026-10-31T00:00:00Z")

        fun advance(duration: Duration) {
            now = now.plus(duration)
        }

        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = now
    }

    private companion object {
        const val NAMESPACE = "reaper"
        const val TTL = 30L
    }
}
