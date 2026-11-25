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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.server.lease.LeaseReaper
import me.ahoo.cosky.store.Position
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.StoreUnavailableException
import me.ahoo.cosky.store.TimelineDivergedException
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.time.Duration.Companion.seconds

/**
 * A replica stays ready while the store is away, on purpose: `cosky_store_failures_total` is what shows one that
 * cannot reach it (design §7.9).
 */
class MeteredStoreTest {
    private val registry = SimpleMeterRegistry()
    private val metrics = CoSkyMetrics(registry)

    /** Out of reach, whatever it is asked. */
    private val down = Proxy.newProxyInstance(Store::class.java.classLoader, arrayOf(Store::class.java), ::unreachable)
        as Store

    @Suppress("UNUSED_PARAMETER")
    private fun unreachable(proxy: Any, method: Method, arguments: Array<Any?>?): Any {
        if (method.returnType == Flow::class.java) {
            return flow<Nothing> { throw StoreUnavailableException("Redis: down") }
        }
        throw StoreUnavailableException("Redis: down")
    }

    private fun failures(): Map<String, Double> = registry.find("cosky_store_failures_total").counters()
        .associate { checkNotNull(it.id.getTag("operation")) to it.count() }

    @Test
    fun `each operation that cannot reach the store counts, by the name the store gives it`() = runBlocking<Unit> {
        val metered = MeteredStore(down, metrics)
        val key = ResourceKey.newBuilder().setNamespace(NAMESPACE).setKind(Kind.KIND_CONFIG).setName("a").build()
        val calls: List<suspend () -> Any?> = listOf(
            { metered.apply(NAMESPACE, emptyList(), ACTOR) },
            { metered.position(NAMESPACE) },
            { metered.readAll(NAMESPACE).toList() },
            { metered.versions(NAMESPACE).toList() },
            { metered.changes(NAMESPACE, Position("epoch", 0)).toList() },
            { metered.recentChanges(NAMESPACE, 0, 1) },
            { metered.rotateEpoch(NAMESPACE, "epoch") },
            { metered.recordTopology(NAMESPACE, emptyList(), 1.seconds) },
            { metered.topology(NAMESPACE) },
            { metered.history(key, 1) },
            { metered.grantLease(NAMESPACE, 30.seconds, ACTOR) },
            { metered.leaseOwners(NAMESPACE, listOf(1)) },
            { metered.keepAlive(NAMESPACE, listOf(1)) },
            { metered.expireLease(NAMESPACE, 1, ACTOR, force = false) },
            { metered.expiredLeases(NAMESPACE, 1) },
            { metered.leaseStats(NAMESPACE) },
            { metered.createNamespace(NAMESPACE) },
            { metered.namespaceState(NAMESPACE) },
            { metered.terminateNamespace(NAMESPACE, "epoch") },
            { metered.dropNamespace(NAMESPACE, "epoch") },
            { metered.tryLock(LeaseReaper.LOCK, ACTOR, 1.seconds) },
            { metered.unlock(LeaseReaper.LOCK, ACTOR) },
        )
        calls.forEach { call ->
            runCatching { call() }.exceptionOrNull().assert().isInstanceOf(StoreUnavailableException::class.java)
        }

        // An operation the store gains is counted too, once it is called above.
        val operations = Store::class.declaredMemberFunctions.map { it.name }.toSet() - "close"
        failures().assert().isEqualTo(operations.associateWith { 1.0 })
    }

    @Test
    fun `only the store out of reach counts, not what else a call fails with or one that succeeds`() =
        runBlocking<Unit> {
            val memory = MemoryStore().apply { createNamespace(NAMESPACE) }
            val diverged = object : Store by memory {
                override fun readAll(namespace: String): Flow<Resource> =
                    flow { throw TimelineDivergedException(Position("another", 1)) }
            }
            val metered = MeteredStore(diverged, metrics)
            metered.position(NAMESPACE).assert().isNotNull()
            runCatching { metered.readAll(NAMESPACE).toList() }.exceptionOrNull()
                .assert().isInstanceOf(TimelineDivergedException::class.java)

            failures().assert().isEmpty()
        }

    @Test
    fun `a replica cut off from the store counts with nothing else to do, as its reaper asks for the lock`() =
        runBlocking<Unit> {
            val reaper = LeaseReaper(MeteredStore(down, metrics), { listOf(NAMESPACE) })
            repeat(2) { runCatching { reaper.tick() } }

            failures().assert().isEqualTo(mapOf("tryLock" to 2.0))
        }

    private companion object {
        const val NAMESPACE = "observed"
        const val ACTOR = "test"
    }
}
