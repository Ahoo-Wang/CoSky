package me.ahoo.govern.discovery.loadbalancer;

import me.ahoo.govern.core.util.Futures;
import me.ahoo.govern.discovery.ServiceInstance;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
public interface LoadBalancer {
    int ZERO = 0;
    int ONE = 1;

    CompletableFuture<ServiceInstance> choose(String namespace, String serviceId);

    default ServiceInstance choose(String namespace, String serviceId, Duration timeout) {
        return Futures.getUnChecked(choose(namespace, serviceId), timeout);
    }

    interface Chooser {
        ServiceInstance choose();
    }
}
