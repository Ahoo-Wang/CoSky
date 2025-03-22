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
import me.ahoo.cosky.discovery.loadbalancer.BinaryWeightRandomLoadBalancer.BinaryChooser
import java.util.*
import java.util.concurrent.ThreadLocalRandom

/**
 * Binary Weight Random Load Balancer.
 *
 * @author ahoo wang
 */
class BinaryWeightRandomLoadBalancer(
    serviceDiscovery: ServiceDiscovery,
    instanceEventListenerContainer: InstanceEventListenerContainer
) :
    AbstractLoadBalancer<BinaryChooser>(serviceDiscovery, instanceEventListenerContainer) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun createChooser(serviceInstances: List<ServiceInstance>): BinaryChooser {
        return BinaryChooser(serviceInstances)
    }

    class BinaryChooser(private val instanceList: List<ServiceInstance>) : LoadBalancer.Chooser {
        private val totalWeight: Int
        private val randomBound: Int
        private val weightLine: IntArray = IntArray(instanceList.size)
        private val maxLineIndex: Int = instanceList.size - 1

        init {
            var accWeight = LoadBalancer.ZERO
            for (i in instanceList.indices) {
                val instanceWeight = instanceList[i].weight
                if (instanceWeight == LoadBalancer.ZERO) {
                    continue
                }
                accWeight += instanceWeight
                weightLine[i] = accWeight
            }
            totalWeight = accWeight
            randomBound = totalWeight + LoadBalancer.ONE
        }

        override fun choose(): ServiceInstance? {
            if (weightLine.size == LoadBalancer.ZERO) {
                log.warn {
                    "choose - The size of connector instances is zero!"
                }
                return null
            }
            if (LoadBalancer.ZERO == totalWeight) {
                log.warn { "choose - The size of connector instances is [${weightLine.size}],but total weight is 0!" }
                return null
            }
            if (weightLine.size == LoadBalancer.ONE) {
                return instanceList[LoadBalancer.ZERO]
            }
            val randomValue = ThreadLocalRandom.current().nextInt(LoadBalancer.ONE, randomBound)
            if (randomValue == LoadBalancer.ONE) {
                return instanceList[LoadBalancer.ZERO]
            }
            if (randomValue == totalWeight) {
                return instanceList[maxLineIndex]
            }
            val instanceIdx = binarySearchLowIndex(randomValue)
            return instanceList[instanceIdx]
        }

        private fun binarySearchLowIndex(randomValue: Int): Int {
            var idx = Arrays.binarySearch(weightLine, randomValue)
            if (idx < 0) {
                idx = -idx - 1
            }
            return idx
        }
    }
}
