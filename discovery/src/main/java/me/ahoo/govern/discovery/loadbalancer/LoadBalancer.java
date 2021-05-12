package me.ahoo.govern.discovery.loadbalancer;

import me.ahoo.govern.discovery.ServiceInstance;

import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
public interface LoadBalancer {
    int ZERO = 0;
    int ONE = 1;

    CompletableFuture<ServiceInstance> choose(String namespace, String serviceId);

    interface Chooser {
        ServiceInstance choose();
    }
}
