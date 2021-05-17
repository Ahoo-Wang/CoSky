package me.ahoo.govern.discovery.loadbalancer;

import me.ahoo.govern.discovery.NamespacedServiceId;
import me.ahoo.govern.discovery.ServiceChangedListener;
import me.ahoo.govern.discovery.ServiceInstance;
import me.ahoo.govern.discovery.redis.ConsistencyRedisServiceDiscovery;

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
        public void onChange(NamespacedServiceId namespacedServiceId, String op) {
            serviceMapChooser.computeIfPresent(namespacedServiceId, (key, value) -> serviceDiscovery.getInstances(namespacedServiceId.getNamespace(), namespacedServiceId.getServiceId())
                    .thenApply(serviceInstances -> createChooser(serviceInstances)));

        }
    }
}
