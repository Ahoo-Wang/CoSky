/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

import lombok.var;
import me.ahoo.cosky.discovery.NamespacedServiceId;
import me.ahoo.cosky.discovery.ServiceChangedEvent;
import me.ahoo.cosky.discovery.ServiceChangedListener;
import me.ahoo.cosky.discovery.ServiceInstance;
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author ahoo wang
 */
public abstract class AbstractLoadBalancer<Chooser extends LoadBalancer.Chooser> implements LoadBalancer {

    private final ConcurrentHashMap<NamespacedServiceId, CompletableFuture<Chooser>> serviceMapChooser;
    private final ConsistencyRedisServiceDiscovery serviceDiscovery;

    public AbstractLoadBalancer(ConsistencyRedisServiceDiscovery serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
        serviceMapChooser = new ConcurrentHashMap<>();
    }

    @Override
    public CompletableFuture<ServiceInstance> choose(String namespace, String serviceId) {
        return serviceMapChooser.computeIfAbsent(NamespacedServiceId.of(namespace, serviceId),
                namespacedServiceId -> {
                    serviceDiscovery.addListener(namespacedServiceId, new Listener());
                    return serviceDiscovery.getInstances(namespace, serviceId)
                            .thenApply(serviceInstances -> createChooser(serviceInstances));
                })
                .thenApply(chooser -> chooser.choose());
    }


    protected abstract Chooser createChooser(List<ServiceInstance> serviceInstances);

    private class Listener implements ServiceChangedListener {

        @Override
        public void onChange(ServiceChangedEvent serviceChangedEvent) {
            var namespacedServiceId = serviceChangedEvent.getNamespacedServiceId();
            serviceMapChooser.computeIfPresent(namespacedServiceId, (key, value) -> serviceDiscovery.getInstances(namespacedServiceId.getNamespace(), namespacedServiceId.getServiceId())
                    .thenApply(serviceInstances -> createChooser(serviceInstances)));

        }
    }
}
