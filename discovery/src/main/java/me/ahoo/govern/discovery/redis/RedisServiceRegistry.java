package me.ahoo.govern.discovery.redis;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.govern.core.NamespacedContext;
import me.ahoo.govern.discovery.*;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * @author ahoo wang
 */
@Slf4j
public class RedisServiceRegistry implements ServiceRegistry {

    private final RegistryProperties registryProperties;
    private final RedisClusterAsyncCommands<String, String> redisCommands;
    private final CopyOnWriteArraySet<NamespacedServiceInstance> registeredEphemeralInstances;

    public RedisServiceRegistry(RegistryProperties registryProperties,
                                RedisClusterAsyncCommands<String, String> redisCommands) {
        this.registeredEphemeralInstances = new CopyOnWriteArraySet<>();
        this.registryProperties = registryProperties;
        this.redisCommands = redisCommands;
    }

    private CompletableFuture<Boolean> register0(String namespace, String scriptSha, ServiceInstance serviceInstance) {
        /**
         * KEYS[1]
         */
        String[] keys =
                {
                        /**
                         * local namespace = KEYS[1];
                        */
                        namespace,
                        /**
                         * local instanceTtl = KEYS[2];
                        */
                        serviceInstance.isEphemeral() ? String.valueOf(registryProperties.getInstanceTtl()) : "-1",
                        /**
                         * local serviceId = KEYS[3];
                        */
                        serviceInstance.getServiceId(),
                        /**
                         * local instanceId = KEYS[4];
                        */
                        serviceInstance.getInstanceId(),
                        /**
                         * local scheme = KEYS[5];
                        */
                        serviceInstance.getSchema(),
                        /**
                         * local ip = KEYS[6];
                        */
                        serviceInstance.getIp(),
                        /**
                         * local port = KEYS[7];
                        */
                        String.valueOf(serviceInstance.getPort()),
                        /**
                         * local weight = KEYS[8];
                        */
                        String.valueOf(serviceInstance.getWeight()),
                };

        /**
         * ARGV[1]
         */
        String[] values = ServiceInstanceCodec.encodeMetadata(serviceInstance.getMetadata());

        RedisFuture<Boolean> redisFuture = redisCommands.evalsha(scriptSha, ScriptOutputType.BOOLEAN, keys, values);
        return redisFuture.toCompletableFuture();
    }

    /**
     * @param serviceInstance 服务实例
     */
    @Override
    public CompletableFuture<Boolean> register(ServiceInstance serviceInstance) {
        return register(NamespacedContext.GLOBAL.getNamespace(), serviceInstance);
    }

    @Override
    public CompletableFuture<Boolean> register(String namespace, ServiceInstance serviceInstance) {
        if (log.isInfoEnabled()) {
            log.info("register - instanceId:[{}]  @ namespace:[{}].", serviceInstance.getInstanceId(), namespace);
        }

        addEphemeralInstance(namespace, serviceInstance);
        return DiscoveryRedisScripts.loadRegistryRegister(redisCommands)
                .thenCompose(sha -> register0(namespace, sha, serviceInstance));
    }

    private void addEphemeralInstance(String namespace, ServiceInstance serviceInstance) {
        if (!serviceInstance.isEphemeral()) {
            return;
        }
        registeredEphemeralInstances.add(NamespacedServiceInstance.of(namespace, serviceInstance));
    }

    private void removeEphemeralInstance(String namespace, String instanceId) {
        var serviceInstanceOp = registeredEphemeralInstances.stream()
                .filter(namespacedServiceInstance -> namespacedServiceInstance.getNamespace().equals(namespace) &&
                        instanceId.equals(namespacedServiceInstance.getServiceInstance().getInstanceId()))
                .findFirst();
        if (serviceInstanceOp.isPresent()) {
            registeredEphemeralInstances.remove(serviceInstanceOp.get());
        }
    }

    private void removeEphemeralInstance(String namespace, ServiceInstance serviceInstance) {
        if (!serviceInstance.isEphemeral()) {
            return;
        }

        registeredEphemeralInstances.remove(NamespacedServiceInstance.of(namespace, serviceInstance));
    }

    @Override
    public Set<NamespacedServiceInstance> getRegisteredEphemeralInstances() {
        return registeredEphemeralInstances;
    }


    @Override
    public CompletableFuture<Boolean> setMetadata(String serviceId, String instanceId, String key, String value) {
        return setMetadata(NamespacedContext.GLOBAL.getNamespace(), serviceId, instanceId, key, value);
    }

    @Override
    public CompletableFuture<Boolean> setMetadata(String namespace, String serviceId, String instanceId, String key, String value) {
        String[] values = {key, value};
        return setMetadata0(namespace, instanceId, values);
    }

    @Override
    public CompletableFuture<Boolean> setMetadata(String serviceId, String instanceId, Map<String, String> metadata) {
        return setMetadata(NamespacedContext.GLOBAL.getNamespace(), serviceId, instanceId, metadata);
    }

    @Override
    public CompletableFuture<Boolean> setMetadata(String namespace, String serviceId, String instanceId, Map<String, String> metadata) {
        String[] values = ServiceInstanceCodec.encodeMetadata(metadata);
        return setMetadata0(namespace, instanceId, values);
    }

    private CompletableFuture<Boolean> setMetadata0(String namespace, String instanceId, String[] metadata) {
        if (log.isInfoEnabled()) {
            log.info("setMetadata - instanceId:[{}] @ namespace:[{}].", instanceId, namespace);
        }
        String[] keys = new String[]{namespace, instanceId};
        return DiscoveryRedisScripts.loadRegistrySetMetadata(redisCommands)
                .thenCompose(sha ->
                        redisCommands.evalsha(sha, ScriptOutputType.BOOLEAN, keys, metadata));
    }


    @Override
    public CompletableFuture<Boolean> renew(ServiceInstance serviceInstance) {
        return renew(NamespacedContext.GLOBAL.getNamespace(), serviceInstance);
    }

    @Override
    public CompletableFuture<Boolean> renew(String namespace, ServiceInstance serviceInstance) {
        if (log.isInfoEnabled()) {
            log.info("renew - instanceId:[{}] @ namespace:[{}].", serviceInstance.getInstanceId(), namespace);
        }

        if (!serviceInstance.isEphemeral()) {
            log.warn("renew - instanceId:[{}] @ namespace:[{}] is not ephemeral, can not renew.", serviceInstance.getInstanceId(), namespace);
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
        String[] keys = new String[]{namespace, serviceInstance.getInstanceId(), String.valueOf(registryProperties.getInstanceTtl())};
        return DiscoveryRedisScripts.loadRegistryRenew(redisCommands)
                .thenCompose(sha ->
                        redisCommands.evalsha(sha, ScriptOutputType.BOOLEAN, keys));
    }


    @Override
    public CompletableFuture<Boolean> deregister(String serviceId, String instanceId) {
        return deregister(NamespacedContext.GLOBAL.getNamespace(), serviceId, instanceId);
    }

    @Override
    public CompletableFuture<Boolean> deregister(String namespace, String serviceId, String instanceId) {
        if (log.isInfoEnabled()) {
            log.info("deregister - instanceId:[{}] @ namespace:[{}].", instanceId, namespace);
        }
        removeEphemeralInstance(namespace, instanceId);

        return deregister0(namespace, serviceId, instanceId);
    }

    private CompletableFuture<Boolean> deregister0(String namespace, String serviceId, String instanceId) {
        return DiscoveryRedisScripts.loadRegistryDeregister(redisCommands)
                .thenCompose(sha -> {
                    String[] keys = {namespace, serviceId, instanceId};
                    RedisFuture<Boolean> redisFuture = redisCommands.evalsha(sha, ScriptOutputType.BOOLEAN, keys);
                    return redisFuture;
                });
    }

    @Override
    public CompletableFuture<Boolean> deregister(ServiceInstance serviceInstance) {
        return deregister(NamespacedContext.GLOBAL.getNamespace(), serviceInstance);
    }

    @Override
    public CompletableFuture<Boolean> deregister(String namespace, ServiceInstance serviceInstance) {
        if (log.isInfoEnabled()) {
            log.info("deregister - instanceId:[{}] @ namespace:[{}].", serviceInstance.getInstanceId(), namespace);
        }

        removeEphemeralInstance(namespace, serviceInstance);
        return deregister0(namespace, serviceInstance.getServiceId(), serviceInstance.getInstanceId());
    }
}
