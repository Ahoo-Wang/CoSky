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

import me.ahoo.cosky.discovery.NamespacedServiceId
import me.ahoo.cosky.discovery.ServiceDiscovery
import me.ahoo.cosky.discovery.ServiceInstance
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

/**
 * Abstract Load Balancer.
 *
 * Choosers are derived from the snapshots of [serviceDiscovery] and reused for as long as the snapshot is the same
 * list instance, so the load balancer can never be staler than the discovery cache.
 *
 * @author ahoo wang
 */
abstract class AbstractLoadBalancer<C : LoadBalancer.Chooser>(
    private val serviceDiscovery: ServiceDiscovery
) : LoadBalancer {

    private class CachedChooser<C>(val instances: List<ServiceInstance>, val chooser: C)

    private val choosers = ConcurrentHashMap<NamespacedServiceId, CachedChooser<C>>()

    override fun choose(namespace: String, serviceId: String): Mono<ServiceInstance> {
        val namespacedServiceId = NamespacedServiceId(namespace, serviceId)
        return serviceDiscovery.getInstances(namespace, serviceId)
            .mapNotNull { chooserOf(namespacedServiceId, it).choose() }
    }

    private fun chooserOf(namespacedServiceId: NamespacedServiceId, instances: List<ServiceInstance>): C {
        val cached = choosers[namespacedServiceId]
        if (cached != null && cached.instances === instances) {
            return cached.chooser
        }
        return createChooser(instances).also {
            choosers[namespacedServiceId] = CachedChooser(instances, it)
        }
    }

    protected abstract fun createChooser(serviceInstances: List<ServiceInstance>): C
}
