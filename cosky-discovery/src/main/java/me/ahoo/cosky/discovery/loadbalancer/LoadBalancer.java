package me.ahoo.cosky.discovery.loadbalancer;

import me.ahoo.cosky.discovery.ServiceInstance;
import me.ahoo.cosky.core.util.Futures;

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
