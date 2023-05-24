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
import me.ahoo.cosky.discovery.NamespacedServiceId
import me.ahoo.cosky.discovery.ServiceDiscovery
import me.ahoo.cosky.discovery.ServiceInstance
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

/**
 * Abstract Load Balancer.
 *
 * @author ahoo wang
 */
abstract class AbstractLoadBalancer<C : LoadBalancer.Chooser>(
    private val serviceDiscovery: ServiceDiscovery,
    private val instanceEventListenerContainer: InstanceEventListenerContainer
) : LoadBalancer {
    private val serviceMapChooser: ConcurrentHashMap<NamespacedServiceId, Mono<C>> = ConcurrentHashMap()

    private fun ensureChooser(namespacedServiceId: NamespacedServiceId): Mono<C> {
        return serviceMapChooser.computeIfAbsent(
            namespacedServiceId,
        ) { key: NamespacedServiceId ->
            @Suppress("CallingSubscribeInNonBlockingScope")
            instanceEventListenerContainer.receive(key)
                .doOnNext {
                    @Suppress("ReactiveStreamsUnusedPublisher")
                    serviceMapChooser[key] = getCachedInstances(namespacedServiceId)
                }
                .subscribe()
            getCachedInstances(namespacedServiceId)
        }
    }

    private fun getCachedInstances(namespacedServiceId: NamespacedServiceId): Mono<C> {
        return serviceDiscovery.getInstances(namespacedServiceId.namespace, namespacedServiceId.serviceId)
            .collectList()
            .map { createChooser(it) }
            .cache()
    }

    override fun choose(namespace: String, serviceId: String): Mono<ServiceInstance> {
        return ensureChooser(NamespacedServiceId(namespace, serviceId))
            .mapNotNull { it.choose() }
    }

    protected abstract fun createChooser(serviceInstances: List<ServiceInstance>): C
}
