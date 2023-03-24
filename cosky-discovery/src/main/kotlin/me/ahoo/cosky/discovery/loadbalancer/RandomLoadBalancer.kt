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

import me.ahoo.cosky.discovery.InstanceEventListenerContainer
import me.ahoo.cosky.discovery.ServiceDiscovery
import me.ahoo.cosky.discovery.ServiceInstance
import me.ahoo.cosky.discovery.loadbalancer.RandomLoadBalancer.RandomChooser
import java.util.concurrent.ThreadLocalRandom

/**
 * Random Load Balancer.
 *
 * @author ahoo wang
 */
class RandomLoadBalancer(
    serviceDiscovery: ServiceDiscovery,
    instanceEventListenerContainer: InstanceEventListenerContainer,
) :
    AbstractLoadBalancer<RandomChooser>(serviceDiscovery, instanceEventListenerContainer) {
    override fun createChooser(serviceInstances: List<ServiceInstance>): RandomChooser {
        return RandomChooser(serviceInstances)
    }

    class RandomChooser(private val serviceInstances: List<ServiceInstance>) : LoadBalancer.Chooser {
        override fun choose(): ServiceInstance? {
            if (serviceInstances.isEmpty()) {
                return null
            }
            if (serviceInstances.size == LoadBalancer.ONE) {
                return serviceInstances[LoadBalancer.ZERO]
            }
            val randomIdx = ThreadLocalRandom.current().nextInt(serviceInstances.size)
            return serviceInstances[randomIdx]
        }
    }
}
