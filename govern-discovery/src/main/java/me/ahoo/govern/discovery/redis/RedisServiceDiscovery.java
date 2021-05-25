package me.ahoo.govern.discovery.redis;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.govern.core.NamespacedContext;
import me.ahoo.govern.discovery.DiscoveryKeyGenerator;
import me.ahoo.govern.discovery.ServiceDiscovery;
import me.ahoo.govern.discovery.ServiceInstance;
import me.ahoo.govern.discovery.ServiceInstanceCodec;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
@Slf4j
public class RedisServiceDiscovery implements ServiceDiscovery {
    private final RedisClusterAsyncCommands<String, String> redisCommands;

    public RedisServiceDiscovery(
            RedisClusterAsyncCommands<String, String> redisCommands) {
        this.redisCommands = redisCommands;
    }

    @Override
    public CompletableFuture<List<ServiceInstance>> getInstances(String serviceId) {
        return getInstances(NamespacedContext.GLOBAL.getNamespace(), serviceId);
    }

    @Override
    public CompletableFuture<List<ServiceInstance>> getInstances(String namespace, String serviceId) {
        return DiscoveryRedisScripts.doDiscoveryGetInstances(redisCommands, sha -> {
            String[] keys = {namespace};
            String[] values = {serviceId};
            return redisCommands.<List<List<String>>>evalsha(sha, ScriptOutputType.MULTI, keys, values);
        })
        .thenApply(instanceGroups -> {
            if (Objects.isNull(instanceGroups)) {
                return Collections.emptyList();
            }
            ArrayList<ServiceInstance> instances = new ArrayList<>(instanceGroups.size());
            instanceGroups.forEach(instanceData -> instances.add(ServiceInstanceCodec.decode(instanceData)));
            return instances;
        });
    }

    @Override
    public CompletableFuture<ServiceInstance> getInstance(String namespace, String serviceId, String instanceId) {
        return DiscoveryRedisScripts.doDiscoveryGetInstance(redisCommands, sha -> {
            String[] keys = {namespace};
            String[] values = {serviceId, instanceId};
            return redisCommands.<List<String>>evalsha(sha, ScriptOutputType.MULTI, keys, values);
        })
        .thenApply(instanceData -> {
            if (Objects.isNull(instanceData)) {
                return null;
            }
            return ServiceInstanceCodec.decode(instanceData);
        });
    }

    @Override
    public CompletableFuture<Long> getInstanceTtl(String namespace, String serviceId, String instanceId) {
        return DiscoveryRedisScripts.doDiscoveryGetInstanceTtl(redisCommands, sha -> {
            String[] keys = {namespace};
            String[] values = {serviceId, instanceId};
            return redisCommands.evalsha(sha, ScriptOutputType.INTEGER, keys, values);
        });
    }

    @Override
    public CompletableFuture<Set<String>> getServices(String namespace) {
        var serviceIdxKey = DiscoveryKeyGenerator.getServiceIdxKey(namespace);
        return redisCommands.smembers(serviceIdxKey).toCompletableFuture();
    }

    @Override
    public CompletableFuture<Set<String>> getServices() {
        return getServices(NamespacedContext.GLOBAL.getNamespace());
    }


}
