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
import io.mockk.verify
import me.ahoo.cosky.discovery.ServiceInstance.Companion.asServiceInstance
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.time.Duration

class ConsistencyRenewEventTest {

    @Test
    fun renewCarryingTtlAtNeedsNoRedisRoundTrip() {
        val now = System.currentTimeMillis() / 1000
        val instance = Instance.asInstance("svc", "http", "127.0.0.1", 8080).asServiceInstance(ttlAt = now + 100)
        val delegate = mockk<ServiceDiscovery> {
            every { getInstances("ns", "svc") } returns Mono.just(listOf(instance))
        }
        val events = Sinks.many().multicast().directBestEffort<InstanceChangedEvent>()
        val discovery = ConsistencyRedisServiceDiscovery(
            delegate = delegate,
            serviceEventListenerContainer = mockk(),
            instanceEventListenerContainer = mockk { every { listen(any()) } returns Mono.just(events.asFlux()) },
        )
        discovery.getInstances("ns", "svc").block()

        val renewedTtlAt = now + 200
        events.tryEmitNext(
            InstanceChangedEvent(
                NamespacedServiceId("ns", "svc"),
                InstanceChangedEvent.Event.RENEW,
                instance,
                renewedTtlAt
            ),
        )

        Mono.defer { discovery.getInstances("ns", "svc") }
            .filter { it.single().ttlAt == renewedTtlAt }
            .repeatWhenEmpty { it.delayElements(Duration.ofMillis(20)) }
            .block(Duration.ofSeconds(5))
        verify(exactly = 0) { delegate.getInstanceTtl(any(), any(), any()) }
        discovery.close()
    }
}
