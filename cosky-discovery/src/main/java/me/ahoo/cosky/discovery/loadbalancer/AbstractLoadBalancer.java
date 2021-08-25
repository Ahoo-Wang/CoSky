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

import me.ahoo.cosky.discovery.NamespacedServiceId;
import me.ahoo.cosky.discovery.ServiceChangedEvent;
import me.ahoo.cosky.discovery.ServiceChangedListener;
import me.ahoo.cosky.discovery.ServiceInstance;
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author ahoo wang
 */
public abstract class AbstractLoadBalancer<Chooser extends LoadBalancer.Chooser> implements LoadBalancer {

    private final ConcurrentHashMap<NamespacedServiceId, Mono<Chooser>> serviceMapChooser;
    private final ConsistencyRedisServiceDiscovery serviceDiscovery;

    public AbstractLoadBalancer(ConsistencyRedisServiceDiscovery serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
        serviceMapChooser = new ConcurrentHashMap<>();
    }

    private Mono<Chooser> ensureChooser(NamespacedServiceId namespacedServiceId) {
        return serviceMapChooser.computeIfAbsent(namespacedServiceId,
                key -> {
                    serviceDiscovery.addListener(key, new Listener());
                    return serviceDiscovery.getInstances(key.getNamespace(), key.getServiceId())
                            .map(this::createChooser)
                            .cache();
                });
    }

    @Override
    public Mono<ServiceInstance> choose(String namespace, String serviceId) {
        return ensureChooser(NamespacedServiceId.of(namespace, serviceId))
                .mapNotNull(LoadBalancer.Chooser::choose);
    }


    protected abstract Chooser createChooser(List<ServiceInstance> serviceInstances);

    private class Listener implements ServiceChangedListener {

        @Override
        public void onChange(ServiceChangedEvent serviceChangedEvent) {
            NamespacedServiceId namespacedServiceId = serviceChangedEvent.getNamespacedServiceId();
            serviceMapChooser.remove(namespacedServiceId);
            ensureChooser(namespacedServiceId);

        }
    }
}
