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

import me.ahoo.cosky.discovery.ServiceInstance
import me.ahoo.cosky.discovery.loadbalancer.BinaryWeightRandomLoadBalancer.BinaryChooser
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ThreadLocalRandom

/**
 * Binary Weight Random Load Balancer.
 *
 * @author ahoo wang
 */
class BinaryWeightRandomLoadBalancer(serviceDiscovery: ConsistencyRedisServiceDiscovery) :
    AbstractLoadBalancer<BinaryChooser>(serviceDiscovery) {
    companion object {
        private val log = LoggerFactory.getLogger(BinaryWeightRandomLoadBalancer::class.java)
    }

    override fun createChooser(serviceInstances: List<ServiceInstance>): BinaryChooser {
        return BinaryChooser(serviceInstances)
    }

    class BinaryChooser(private val instanceList: List<ServiceInstance>) : LoadBalancer.Chooser {
        private val totalWeight : Int
        private val randomBound  : Int
        private val weightLine: IntArray
        private val maxLineIndex: Int

        init {
            maxLineIndex = instanceList.size - 1
            weightLine = IntArray(instanceList.size)
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
                if (log.isWarnEnabled) {
                    log.warn("choose - The size of connector instances is [{}]!", weightLine.size)
                }
                return null
            }
            if (LoadBalancer.ZERO == totalWeight) {
                log.warn("choose - The size of connector instances is [{}],but total weight is 0!", weightLine.size)
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
