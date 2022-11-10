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
import me.ahoo.cosky.discovery.loadbalancer.TreeWeightRandomLoadBalancer.TreeChooser
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ThreadLocalRandom

/**
 * Tree Weight Random Load Balancer.
 *
 * @author ahoo wang
 */
class TreeWeightRandomLoadBalancer(
    serviceDiscovery: ServiceDiscovery,
    instanceEventListenerContainer: InstanceEventListenerContainer
) :
    AbstractLoadBalancer<TreeChooser>(serviceDiscovery, instanceEventListenerContainer) {
    companion object {
        private val log = LoggerFactory.getLogger(TreeWeightRandomLoadBalancer::class.java)
    }

    override fun createChooser(serviceInstances: List<ServiceInstance>): TreeChooser {
        return TreeChooser(serviceInstances)
    }

    class TreeChooser(instanceList: List<ServiceInstance>) : LoadBalancer.Chooser {
        private val instanceTree: TreeMap<Int, ServiceInstance> = TreeMap()
        private val totalWeight: Int

        init {
            var accWeight = LoadBalancer.ZERO
            for (instance in instanceList) {
                if (instance.weight == LoadBalancer.ZERO) {
                    continue
                }
                accWeight += instance.weight
                instanceTree[accWeight] = instance
            }
            totalWeight = accWeight
        }

        override fun choose(): ServiceInstance? {
            if (instanceTree.size == LoadBalancer.ZERO) {
                if (log.isWarnEnabled) {
                    log.warn("choose - The size of connector instances is [{}]!", instanceTree.size)
                }
                return null
            }
            if (LoadBalancer.ZERO == totalWeight) {
                log.warn("choose - The size of connector instances is [{}],but total weight is 0!", instanceTree.size)
                return null
            }
            if (instanceTree.size == LoadBalancer.ONE) {
                return instanceTree.firstEntry().value
            }
            val randomVal = ThreadLocalRandom.current().nextInt(LoadBalancer.ZERO, totalWeight)
            val tailMap = instanceTree.tailMap(randomVal, false)
            return tailMap.firstEntry().value
        }
    }
}
