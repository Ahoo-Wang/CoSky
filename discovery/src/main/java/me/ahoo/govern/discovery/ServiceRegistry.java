package me.ahoo.govern.discovery;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
public interface ServiceRegistry {

    /**
     * 注册实例
     *
     * @param serviceInstance
     */
    CompletableFuture<Boolean> register(ServiceInstance serviceInstance);

    /**
     * 服务实例续期
     *
     * @param serviceInstance
     */
    CompletableFuture<Boolean> renew(ServiceInstance serviceInstance);

    CompletableFuture<Boolean> deregister(ServiceInstance serviceInstance);

    CompletableFuture<Boolean> deregister(String serviceId, String instanceId);

    Set<ServiceInstance> getRegisteredEphemeralInstances();

    CompletableFuture<Boolean> setMetadata(String serviceId,String instanceId, String key, String value);

    CompletableFuture<Boolean> setMetadata(String serviceId,String instanceId, Map<String, String> metadata);
}
