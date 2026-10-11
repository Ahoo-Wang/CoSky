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
package me.ahoo.cosky.discovery

import io.mockk.every
import io.mockk.mockk
import me.ahoo.cosky.discovery.ServiceInstance.Companion.asServiceInstance
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.test.test
import reactor.util.retry.Retry
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class ConsistencyRedisServiceDiscoveryErrorTest {
    private val instance = Instance.asInstance("svc", "http", "127.0.0.1", 8080)
        .asServiceInstance(isEphemeral = false)

    private fun <T : Any> failFirst(value: T): Mono<T> {
        val times = AtomicInteger()
        return Mono.defer {
            if (times.incrementAndGet() == 1) {
                Mono.error(IllegalStateException("transient redis failure"))
            } else {
                Mono.just(value)
            }
        }
    }

    private fun createDiscovery(delegate: ServiceDiscovery) = ConsistencyRedisServiceDiscovery(
        delegate = delegate,
        serviceEventListenerContainer = mockk { every { listen(any()) } returns Mono.just(Flux.never()) },
        instanceEventListenerContainer = mockk { every { listen(any()) } returns Mono.just(Flux.never()) },
        retry = Retry.fixedDelay(Long.MAX_VALUE, Duration.ofMillis(500)),
    )

    private fun <T : Any> awaitValue(source: () -> Mono<T>): T {
        return Mono.defer(source)
            .onErrorResume { Mono.empty() }
            .repeatWhenEmpty { it.delayElements(Duration.ofMillis(50)) }
            .block(Duration.ofSeconds(5))!!
    }

    @Test
    fun getInstancesShouldNotCacheError() {
        val instances = failFirst(listOf(instance))
        val discovery = createDiscovery(mockk { every { getInstances(any(), any()) } returns instances })

        discovery.getInstances("ns", "svc").test().expectError(IllegalStateException::class.java).verify()
        awaitValue { discovery.getInstances("ns", "svc") }.assert().isEqualTo(listOf(instance))
        discovery.close()
    }

    @Test
    fun getServicesShouldNotCacheError() {
        val services = failFirst(listOf("svc"))
        val discovery = createDiscovery(mockk { every { getServices(any()) } returns services })

        discovery.getServices("ns").test().expectError(IllegalStateException::class.java).verify()
        awaitValue { discovery.getServices("ns") }.assert().isEqualTo(listOf("svc"))
        discovery.close()
    }
}
