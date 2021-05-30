package me.ahoo.cosky.discovery;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
public interface ServiceDiscovery {

    CompletableFuture<Set<String>> getServices(String namespace);

    CompletableFuture<Set<String>> getServices();

    CompletableFuture<List<ServiceInstance>> getInstances(String serviceId);

    CompletableFuture<List<ServiceInstance>> getInstances(String namespace, String serviceId);

    CompletableFuture<ServiceInstance> getInstance(String namespace, String serviceId, String instanceId);

    CompletableFuture<Long> getInstanceTtl(String namespace, String serviceId, String instanceId);

}
