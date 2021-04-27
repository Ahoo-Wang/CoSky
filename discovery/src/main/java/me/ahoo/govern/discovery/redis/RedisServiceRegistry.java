package me.ahoo.govern.discovery.redis;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
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

    private final DiscoveryKeyGenerator keyGenerator;

    private final RegistryProperties registryProperties;
    private final RedisClusterAsyncCommands<String, String> redisCommands;
    private final CopyOnWriteArraySet<ServiceInstance> registeredEphemeralInstances;

    public RedisServiceRegistry(RegistryProperties registryProperties,
                                DiscoveryKeyGenerator keyGenerator,
                                RedisClusterAsyncCommands<String, String> redisCommands) {
        this.registeredEphemeralInstances = new CopyOnWriteArraySet<>();
        this.keyGenerator = keyGenerator;
        this.registryProperties = registryProperties;
        this.redisCommands = redisCommands;
    }

    private CompletableFuture<Boolean> register(String scriptSha, ServiceInstance serviceInstance) {
        /**
         * KEYS[1]
         */
        String[] keys =
                {
                        /**
                         * local namespace = KEYS[1];
                        */
                        keyGenerator.getNamespace(),
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
     * 1. 注册服务索引 {@link #registerServiceIdx(String)}
     * 2. 注册服务实例索引 {@link #registerInstanceIdx(String, String)}
     * 3. 注册服务实例 {@link #registerInstance(ServiceInstance)}
     *
     * @param serviceInstance 服务实例
     */
    @Override
    public CompletableFuture<Boolean> register(ServiceInstance serviceInstance) {
        if (log.isInfoEnabled()) {
            log.info("register - instanceId:[{}] .", serviceInstance.getInstanceId());
        }

        addEphemeralInstance(serviceInstance);
        return DiscoveryRedisScripts.loadRegistryRegister(redisCommands)
                .thenCompose(sha -> register(sha, serviceInstance));
    }

    private void addEphemeralInstance(ServiceInstance serviceInstance) {
        if (!serviceInstance.isEphemeral()) {
            return;
        }
        registeredEphemeralInstances.add(serviceInstance);
    }

    private void removeEphemeralInstance(String instanceId) {
        var serviceInstanceOp = registeredEphemeralInstances.stream().filter(instance -> instanceId.equals(instance.getInstanceId())).findFirst();
        if (serviceInstanceOp.isPresent()) {
            registeredEphemeralInstances.remove(serviceInstanceOp.get());
        }
    }

    private void removeEphemeralInstance(ServiceInstance serviceInstance) {
        if (!serviceInstance.isEphemeral()) {
            return;
        }
        registeredEphemeralInstances.remove(serviceInstance);
    }

    @Override
    public Set<ServiceInstance> getRegisteredEphemeralInstances() {
        return registeredEphemeralInstances;
    }


    @Override
    public CompletableFuture<Boolean> setMetadata(String serviceId, String instanceId, String key, String value) {
        if (log.isInfoEnabled()) {
            log.info("setMetadata - instanceId:[{}] .", instanceId);
        }
        var instanceKey = keyGenerator.getInstanceKey(instanceId);
        return redisCommands.hset(instanceKey, key, value).toCompletableFuture();
    }

    @Override
    public CompletableFuture<Boolean> setMetadata(String serviceId, String instanceId, Map<String, String> metadata) {
        if (log.isInfoEnabled()) {
            log.info("setMetadata - instanceId:[{}] .", instanceId);
        }
        var instanceKey = keyGenerator.getInstanceKey(instanceId);
        return redisCommands.hset(instanceKey, metadata)
                .thenApply(setResult -> setResult > 0)
                .toCompletableFuture();
    }

    /**
     * 注册服务实例
     *
     * @param serviceInstance
     */
    public CompletableFuture<Boolean> registerInstance(ServiceInstance serviceInstance) {
        if (log.isInfoEnabled()) {
            log.info("register - instanceId:[{}] .", serviceInstance.getInstanceId());
        }

        addEphemeralInstance(serviceInstance);
        return DiscoveryRedisScripts.loadRegistryRegisterInstance(redisCommands)
                .thenCompose(sha -> register(sha, serviceInstance));
    }

    @Override
    public CompletableFuture<Boolean> renew(ServiceInstance serviceInstance) {
        if (log.isInfoEnabled()) {
            log.info("renew - instanceId:[{}] .", serviceInstance.getInstanceId());
        }

        if (!serviceInstance.isEphemeral()) {
            log.warn("renew - instanceId:[{}] is not ephemeral, can not renew.", serviceInstance.getInstanceId());
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
        var instanceIdKey = keyGenerator.getInstanceKey(serviceInstance.getInstanceId());
        return redisCommands.expire(instanceIdKey, registryProperties.getInstanceTtl()).toCompletableFuture();
    }


    @Override
    public CompletableFuture<Boolean> deregister(String serviceId, String instanceId) {
        if (log.isInfoEnabled()) {
            log.info("deregister - instanceId:[{}] .", instanceId);
        }
        removeEphemeralInstance(instanceId);

        return deregister0(serviceId, instanceId);
    }

    private CompletableFuture<Boolean> deregister0(String serviceId, String instanceId) {
        return DiscoveryRedisScripts.loadRegistryDeregister(redisCommands)
                .thenCompose(sha -> {
                    String[] keys = {keyGenerator.getNamespace(), serviceId, instanceId};
                    RedisFuture<Boolean> redisFuture = redisCommands.evalsha(sha, ScriptOutputType.BOOLEAN, keys);
                    return redisFuture;
                });
    }

    @Override
    public CompletableFuture<Boolean> deregister(ServiceInstance serviceInstance) {
        if (log.isInfoEnabled()) {
            log.info("deregister - instanceId:[{}] .", serviceInstance.getInstanceId());
        }

        removeEphemeralInstance(serviceInstance);
        return deregister0(serviceInstance.getServiceId(), serviceInstance.getInstanceId());
    }

    /**
     * 注册服务索引
     *
     * @param serviceId
     */
    public CompletableFuture<Long> registerServiceIdx(String serviceId) {
        if (log.isInfoEnabled()) {
            log.info("registerServiceIdx - serviceId:[{}] .", serviceId);
        }

        var serviceIdxKey = keyGenerator.getServiceIdxKey();
        return redisCommands.sadd(serviceIdxKey, serviceId).toCompletableFuture();
    }

    /**
     * 注册服务实例索引
     *
     * @param serviceId
     * @param instanceId
     */
    public CompletableFuture<Long> registerInstanceIdx(String serviceId, String instanceId) {
        if (log.isInfoEnabled()) {
            log.info("registerInstanceIdx - serviceId:[{}] - instanceId:[{}].", serviceId, instanceId);
        }

        var serviceInstanceIdxKey = keyGenerator.getInstanceIdxKey(serviceId);
        return redisCommands.sadd(serviceInstanceIdxKey, instanceId).toCompletableFuture();
    }

    @Override
    public String getNamespace() {
        return keyGenerator.getNamespace();
    }
}
