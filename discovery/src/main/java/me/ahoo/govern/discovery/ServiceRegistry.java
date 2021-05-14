package me.ahoo.govern.discovery;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
public interface ServiceRegistry {


    CompletableFuture<Boolean> setService(String namespace, String serviceId);

    CompletableFuture<Boolean> removeService(String namespace, String serviceId);

    /**
     * 注册实例
     *
     * @param serviceInstance
     */
    CompletableFuture<Boolean> register(ServiceInstance serviceInstance);

    CompletableFuture<Boolean> register(String namespace, ServiceInstance serviceInstance);

    /**
     * 服务实例续期
     *
     * @param serviceInstance
     */
    CompletableFuture<Boolean> renew(ServiceInstance serviceInstance);

    CompletableFuture<Boolean> renew(String namespace, ServiceInstance serviceInstance);

    CompletableFuture<Boolean> deregister(ServiceInstance serviceInstance);

    CompletableFuture<Boolean> deregister(String namespace, ServiceInstance serviceInstance);

    CompletableFuture<Boolean> deregister(String serviceId, String instanceId);

    CompletableFuture<Boolean> deregister(String namespace, String serviceId, String instanceId);

    Set<NamespacedServiceInstance> getRegisteredEphemeralInstances();

    CompletableFuture<Boolean> setMetadata(String serviceId, String instanceId, String key, String value);

    CompletableFuture<Boolean> setMetadata(String namespace, String serviceId, String instanceId, String key, String value);

    CompletableFuture<Boolean> setMetadata(String serviceId, String instanceId, Map<String, String> metadata);

    CompletableFuture<Boolean> setMetadata(String namespace, String serviceId, String instanceId, Map<String, String> metadata);
}
