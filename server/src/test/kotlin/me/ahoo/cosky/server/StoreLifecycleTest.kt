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

package me.ahoo.cosky.server

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.resource.ClientResources
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.server.RedisContainer.Companion.REDIS_PORT
import me.ahoo.cosky.server.observe.CoSkyMetrics
import me.ahoo.cosky.store.Store
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.support.DefaultListableBeanFactory

/**
 * What the server opens for its store, it closes with it: a context closed in the same JVM leaves nothing behind.
 */
class StoreLifecycleTest {
    private fun uriOf(redis: RedisContainer) = "redis://${redis.host}:${redis.getMappedPort(REDIS_PORT)}"

    private fun store(redis: RedisContainer, shared: ClientResources? = null): Store {
        val beans = DefaultListableBeanFactory().apply { shared?.let { registerSingleton("clientResources", it) } }
        return CoSkyServerApplication().store(
            CoSkyProperties(store = CoSkyProperties.Store(redis = CoSkyProperties.Redis(uri = uriOf(redis)))),
            beans.getBeanProvider(ClientResources::class.java),
            CoSkyMetrics(SimpleMeterRegistry()),
        )
    }

    /**
     * Counts the other clients of a Redis, and the threads of Lettuce clients in this JVM.
     */
    private class Observer(redis: RedisContainer) : AutoCloseable {
        private val client = RedisClient.create(RedisURI.create(redis.host, redis.getMappedPort(REDIS_PORT)))
        private val connection = client.connect()

        fun others(): Int = connection.sync().clientList().lines().count { it.isNotBlank() } - 1

        fun lettuceThreads(): Int =
            Thread.getAllStackTraces().keys.count { it.isAlive && it.name.startsWith("lettuce") }

        fun await(expected: Int, count: () -> Int) {
            val deadline = System.nanoTime() + WAIT_NANOS
            while (count() != expected && System.nanoTime() < deadline) {
                Thread.sleep(POLL_MILLIS)
            }
            count().assert().isEqualTo(expected)
        }

        override fun close() {
            connection.close()
            client.shutdown()
        }
    }

    @Test
    fun `closing the store closes its connections and the client the server made for it`() {
        RedisContainer().use { redis ->
            redis.start()
            Observer(redis).use { observer ->
                val threads = observer.lettuceThreads()
                val store = store(redis)
                runBlocking { store.createNamespace("lifecycle") }
                observer.others().assert().isEqualTo(2)

                store.close()

                observer.await(0) { observer.others() }
                observer.await(threads) { observer.lettuceThreads() }
            }
        }
    }

    @Test
    fun `resources shared with the server stay open`() {
        RedisContainer().use { redis ->
            redis.start()
            val shared = ClientResources.create()
            try {
                Observer(redis).use { observer ->
                    store(redis, shared).close()
                    observer.await(0) { observer.others() }
                }
                shared.eventExecutorGroup().isShuttingDown.assert().isFalse()
            } finally {
                shared.shutdown()
            }
        }
    }

    @Test
    fun `a store that fails its prerequisites leaves nothing open`() {
        RedisContainer("redis-server", "--maxmemory-policy", "allkeys-lru").use { redis ->
            redis.start()
            Observer(redis).use { observer ->
                val threads = observer.lettuceThreads()

                runCatching { store(redis) }.exceptionOrNull().assert().isNotNull()

                observer.await(0) { observer.others() }
                observer.await(threads) { observer.lettuceThreads() }
            }
        }
    }

    private companion object {
        const val WAIT_NANOS = 5_000_000_000L
        const val POLL_MILLIS = 50L
    }
}
