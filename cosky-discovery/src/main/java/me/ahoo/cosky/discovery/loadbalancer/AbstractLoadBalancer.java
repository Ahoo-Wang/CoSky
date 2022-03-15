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

package me.ahoo.cosky.discovery.loadbalancer;

import me.ahoo.cosky.discovery.NamespacedServiceId;
import me.ahoo.cosky.discovery.ServiceChangedEvent;
import me.ahoo.cosky.discovery.ServiceInstance;
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract Load Balancer.
 *
 * @author ahoo wang
 */
public abstract class AbstractLoadBalancer<C extends LoadBalancer.Chooser> implements LoadBalancer {
    
    private final ConcurrentHashMap<NamespacedServiceId, Mono<C>> serviceMapChooser;
    private final ConsistencyRedisServiceDiscovery serviceDiscovery;
    
    public AbstractLoadBalancer(ConsistencyRedisServiceDiscovery serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
        this.serviceMapChooser = new ConcurrentHashMap<>();
    }
    
    private Mono<C> ensureChooser(NamespacedServiceId namespacedServiceId) {
        return serviceMapChooser.computeIfAbsent(namespacedServiceId,
            key -> {
                serviceDiscovery.listen(key)
                    .doOnNext(serviceChangedEvent -> serviceMapChooser.put(key, getCachedInstances(namespacedServiceId)))
                    .subscribe();
                return getCachedInstances(namespacedServiceId);
            });
    }
    
    private Mono<C> getCachedInstances(NamespacedServiceId namespacedServiceId) {
        return serviceDiscovery.getInstances(namespacedServiceId.getNamespace(), namespacedServiceId.getServiceId())
            .collectList()
            .map(this::createChooser)
            .cache();
    }
    
    @Override
    public Mono<ServiceInstance> choose(String namespace, String serviceId) {
        return ensureChooser(NamespacedServiceId.of(namespace, serviceId))
            .mapNotNull(LoadBalancer.Chooser::choose);
    }
    
    protected abstract C createChooser(List<ServiceInstance> serviceInstances);
}
