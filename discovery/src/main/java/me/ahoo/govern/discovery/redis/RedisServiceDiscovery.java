package me.ahoo.govern.discovery.redis;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.govern.core.NamespacedContext;
import me.ahoo.govern.discovery.*;

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
        return DiscoveryRedisScripts.loadDiscoveryGetInstances(redisCommands)
                .thenCompose(sha -> {
                    RedisFuture<List<List<String>>> redisFuture = redisCommands.evalsha(sha, ScriptOutputType.MULTI, namespace, serviceId);
                    return redisFuture;
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
    public CompletableFuture<Set<String>> getServices(String namespace) {
        var serviceIdxKey = DiscoveryKeyGenerator.getServiceIdxKey(namespace);
        return redisCommands.smembers(serviceIdxKey).toCompletableFuture();
    }

    @Override
    public CompletableFuture<Set<String>> getServices() {
        return getServices(NamespacedContext.GLOBAL.getNamespace());
    }

}
