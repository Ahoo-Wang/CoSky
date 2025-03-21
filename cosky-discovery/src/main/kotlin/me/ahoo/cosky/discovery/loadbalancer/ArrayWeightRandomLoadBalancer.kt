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

import io.github.oshai.kotlinlogging.KotlinLogging
import me.ahoo.cosky.discovery.InstanceEventListenerContainer
import me.ahoo.cosky.discovery.ServiceDiscovery
import me.ahoo.cosky.discovery.ServiceInstance
import me.ahoo.cosky.discovery.loadbalancer.ArrayWeightRandomLoadBalancer.ArrayChooser
import java.util.concurrent.ThreadLocalRandom

/**
 * Array Weight Random Load Balancer.
 *
 * @author ahoo wang
 */
class ArrayWeightRandomLoadBalancer(
    serviceDiscovery: ServiceDiscovery,
    instanceEventListenerContainer: InstanceEventListenerContainer
) :
    AbstractLoadBalancer<ArrayChooser>(serviceDiscovery, instanceEventListenerContainer) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun createChooser(serviceInstances: List<ServiceInstance>): ArrayChooser {
        return ArrayChooser(serviceInstances)
    }

    class ArrayChooser(instanceList: List<ServiceInstance>) : LoadBalancer.Chooser {
        private val instanceLine: Array<ServiceInstance>
        private val totalWeight: Int = if (instanceList.isEmpty()) {
            LoadBalancer.ZERO
        } else {
            instanceList
                .map(ServiceInstance::weight)
                .reduce { a: Int, b: Int -> Integer.sum(a, b) }
        }

        init {
            instanceLine = toLine(instanceList)
        }

        private fun toLine(instanceList: List<ServiceInstance>): Array<ServiceInstance> {
            val line = Array(totalWeight) { instanceList[0] }
            var startX = LoadBalancer.ZERO
            for (connectorInstance in instanceList) {
                val weightLength = connectorInstance.weight
                var idx = LoadBalancer.ZERO
                while (idx < weightLength) {
                    line[startX] = connectorInstance
                    idx++
                    startX++
                }
            }
            return line
        }

        override fun choose(): ServiceInstance? {
            if (instanceLine.size == LoadBalancer.ZERO) {
                log.warn {
                    "choose - The size of connector instances is zero!"
                }
                return null
            }
            if (LoadBalancer.ZERO == totalWeight) {
                log.warn {
                    "choose - The size of connector instances is [${instanceLine.size}],but total weight is 0!"
                }
                return null
            }
            if (instanceLine.size == LoadBalancer.ONE) {
                return instanceLine[LoadBalancer.ZERO]
            }
            val randomValue = ThreadLocalRandom.current().nextInt(0, totalWeight)
            return instanceLine[randomValue]
        }
    }
}
