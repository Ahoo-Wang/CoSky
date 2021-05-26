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

    private RedisFuture<Boolean> register0(String namespace, String scriptSha, ServiceInstance serviceInstance) {
        /**
         * KEYS[1]
         */
        String[] keys = {namespace};
        /**
         * ARGV
         */
        String[] infoArgs = {
                /**
                 * local instanceTtl = ARGV[1];
                */
                serviceInstance.isEphemeral() ? String.valueOf(registryProperties.getInstanceTtl()) : "-1",
                /**
                 * local serviceId = ARGV[2];
                */
                serviceInstance.getServiceId(),
                /**
                 * local instanceId = ARGV[3];
                */
                serviceInstance.getInstanceId(),
                /**
                 * local scheme = ARGV[4];
                */
                serviceInstance.getSchema(),
                /**
                 * local host = ARGV[5];
                */
                serviceInstance.getHost(),
                /**
                 * local port = ARGV[6];
                */
                String.valueOf(serviceInstance.getPort()),
                /**
                 * local weight = ARGV[7];
                */
                String.valueOf(serviceInstance.getWeight())
        };


        String[] values = ServiceInstanceCodec.encodeMetadata(infoArgs, serviceInstance.getMetadata());

        return redisCommands.evalsha(scriptSha, ScriptOutputType.BOOLEAN, keys, values);
    }

    @Override
    public CompletableFuture<Boolean> setService(String namespace, String serviceId) {
        if (log.isInfoEnabled()) {
            log.info("setService - serviceId:[{}]  @ namespace:[{}].", serviceId, namespace);
        }

        return DiscoveryRedisScripts.doRegistrySetService(redisCommands,
                sha -> redisCommands.evalsha(sha, ScriptOutputType.BOOLEAN, new String[]{namespace}, serviceId));

    }

    @Override
    public CompletableFuture<Boolean> removeService(String namespace, String serviceId) {
        if (log.isWarnEnabled()) {
            log.warn("removeService - serviceId:[{}]  @ namespace:[{}].", serviceId, namespace);
        }

        return DiscoveryRedisScripts.doRegistryRemoveService(redisCommands,
                sha -> redisCommands.evalsha(sha, ScriptOutputType.BOOLEAN, new String[]{namespace}, serviceId));
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
        return DiscoveryRedisScripts.doRegistryRegister(redisCommands, sha -> register0(namespace, sha, serviceInstance));
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
        String[] values = {instanceId, key, value};
        return setMetadata0(namespace, instanceId, values);
    }

    @Override
    public CompletableFuture<Boolean> setMetadata(String serviceId, String instanceId, Map<String, String> metadata) {
        return setMetadata(NamespacedContext.GLOBAL.getNamespace(), serviceId, instanceId, metadata);
    }

    @Override
    public CompletableFuture<Boolean> setMetadata(String namespace, String serviceId, String instanceId, Map<String, String> metadata) {
        String[] values = ServiceInstanceCodec.encodeMetadata(new String[]{instanceId}, metadata);
        return setMetadata0(namespace, instanceId, values);
    }

    private CompletableFuture<Boolean> setMetadata0(String namespace, String instanceId, String[] args) {
        if (log.isInfoEnabled()) {
            log.info("setMetadata - instanceId:[{}] @ namespace:[{}].", instanceId, namespace);
        }
        String[] keys = {namespace};
        return DiscoveryRedisScripts.doRegistrySetMetadata(redisCommands, sha ->
                redisCommands.evalsha(sha, ScriptOutputType.BOOLEAN, keys, args));
    }


    @Override
    public CompletableFuture<Boolean> renew(ServiceInstance serviceInstance) {
        return renew(NamespacedContext.GLOBAL.getNamespace(), serviceInstance);
    }

    @Override
    public CompletableFuture<Boolean> renew(String namespace, ServiceInstance serviceInstance) {
        if (log.isDebugEnabled()) {
            log.debug("renew - instanceId:[{}] @ namespace:[{}].", serviceInstance.getInstanceId(), namespace);
        }

        if (!serviceInstance.isEphemeral()) {
            if (log.isWarnEnabled()) {
                log.warn("renew - instanceId:[{}] @ namespace:[{}] is not ephemeral, can not renew.", serviceInstance.getInstanceId(), namespace);
            }
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
        String[] keys = {namespace};
        String[] values = {serviceInstance.getInstanceId(), String.valueOf(registryProperties.getInstanceTtl())};
        return DiscoveryRedisScripts.doRegistryRenew(redisCommands, sha ->
                redisCommands.evalsha(sha, ScriptOutputType.BOOLEAN, keys, values));
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
        return DiscoveryRedisScripts.doRegistryDeregister(redisCommands, sha -> {
            String[] keys = {namespace};
            String[] values = {serviceId, instanceId};
            return redisCommands.evalsha(sha, ScriptOutputType.BOOLEAN, keys, values);
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
