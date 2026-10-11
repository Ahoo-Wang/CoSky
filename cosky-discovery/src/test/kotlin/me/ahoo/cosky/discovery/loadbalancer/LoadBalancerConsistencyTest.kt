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
package me.ahoo.cosky.discovery.loadbalancer

import io.mockk.every
import io.mockk.mockk
import me.ahoo.cosky.discovery.Instance
import me.ahoo.cosky.discovery.ServiceDiscovery
import me.ahoo.cosky.discovery.ServiceInstance
import me.ahoo.cosky.discovery.ServiceInstance.Companion.asServiceInstance
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.kotlin.test.test
import java.util.concurrent.CopyOnWriteArrayList

class LoadBalancerConsistencyTest {

    /**
     * The discovery cache may apply an instance change after the load balancer has observed it.
     * The load balancer must still reflect the discovery cache rather than a stale copy of it.
     */
    @Test
    fun chooseShouldReflectDiscoveryCacheUpdatedAfterEvent() {
        val cachedInstances = CopyOnWriteArrayList<ServiceInstance>()
        val serviceDiscovery = mockk<ServiceDiscovery> {
            every { getInstances(any(), any()) } returns Mono.fromCallable { cachedInstances.toList() }
        }
        val loadBalancer = RandomLoadBalancer(serviceDiscovery)

        loadBalancer.choose("ns", "svc").test().verifyComplete()

        val instance = Instance.asInstance("svc", "http", "127.0.0.1", 8080).asServiceInstance(isEphemeral = false)
        loadBalancer.choose("ns", "svc").test().verifyComplete()
        cachedInstances.add(instance)

        loadBalancer.choose("ns", "svc").test().expectNext(instance).verifyComplete()
    }
}
