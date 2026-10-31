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
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds

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

    private suspend fun register(name: String, ttlSeconds: Long = TTL): Long {
        val lease = (store.grantLease(NAMESPACE, ttlSeconds.seconds, "test") as GrantResult.Granted).lease.id
        store.apply(NAMESPACE, listOf(Mutation.Put(instance(name, lease))), "test")
        return lease
    }

    private fun instance(name: String, lease: Long) = resource {
        meta = objectMeta {
            namespace = NAMESPACE
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

    private suspend fun instances(): List<String> = store.readAll(NAMESPACE).toList().map { it.meta.name }

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

    /**
     * The worst moment for a store to hang: when it answers, what it says has expired only did during the outage.
     */
    private suspend fun midTickHang(hangingStore: (hang: () -> Unit) -> Store) {
        var hanging = false
        val reaper = LeaseReaper(
            hangingStore {
                if (hanging) {
                    clock.advance(Duration.ofSeconds(TTL + 10))
                    hanging = false
                }
            },
            { listOf(NAMESPACE) },
            options,
            clock,
        )
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

    @Test
    fun `a store hanging while it lists what expired does not lead to reaping`() = scenario {
        midTickHang { hang ->
            object : Store by store {
                override suspend fun expiredLeases(namespace: String, limit: Int): List<Long> {
                    hang()
                    return store.expiredLeases(namespace, limit)
                }
            }
        }
    }

    @Test
    fun `a store hanging while it counts leases does not lead to reaping`() = scenario {
        midTickHang { hang ->
            object : Store by store {
                override suspend fun leaseStats(namespace: String): LeaseStats {
                    hang()
                    return store.leaseStats(namespace)
                }
            }
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
    fun `expiry below the threshold is not mass expiry`() = scenario {
        tickEverySecond(31)
        register("short", ttlSeconds = 2)
        repeat(3) { register("long$it", ttlSeconds = 600) }
        tickEverySecond(4)
        instances().assert().containsExactlyInAnyOrder("long0", "long1", "long2")
        reaper.paused.assert().isEmpty()
    }

    @Test
    fun `few leases never count as mass expiry`() = scenario {
        tickEverySecond(31)
        repeat(3) { register("i$it", ttlSeconds = 2) }
        tickEverySecond(4)
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
