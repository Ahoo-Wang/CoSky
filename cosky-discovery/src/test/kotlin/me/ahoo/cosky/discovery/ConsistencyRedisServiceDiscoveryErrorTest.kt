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
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.kotlin.test.test
import java.util.concurrent.atomic.AtomicInteger

class ConsistencyRedisServiceDiscoveryErrorTest {
    private val instance = Instance.asInstance("svc", "http", "127.0.0.1", 8080)
        .asServiceInstance(isEphemeral = false)

    private fun <T : Any> failFirst(value: T): Flux<T> {
        val times = AtomicInteger()
        return Flux.defer {
            if (times.incrementAndGet() == 1) {
                Flux.error(IllegalStateException("transient redis failure"))
            } else {
                Flux.just(value)
            }
        }
    }

    private fun createDiscovery(delegate: ServiceDiscovery) = ConsistencyRedisServiceDiscovery(
        delegate = delegate,
        serviceEventListenerContainer = mockk { every { receive(any()) } returns Flux.never() },
        instanceEventListenerContainer = mockk { every { receive(any()) } returns Flux.never() },
    )

    @Test
    fun getInstancesShouldNotCacheError() {
        val instances = failFirst(instance)
        val discovery = createDiscovery(mockk { every { getInstances(any(), any()) } returns instances })

        discovery.getInstances("ns", "svc").test().expectError(IllegalStateException::class.java).verify()
        discovery.getInstances("ns", "svc").test().expectNext(instance).verifyComplete()
    }

    @Test
    fun getServicesShouldNotCacheError() {
        val services = failFirst("svc")
        val discovery = createDiscovery(mockk { every { getServices(any()) } returns services })

        discovery.getServices("ns").test().expectError(IllegalStateException::class.java).verify()
        discovery.getServices("ns").test().expectNext("svc").verifyComplete()
    }
}
