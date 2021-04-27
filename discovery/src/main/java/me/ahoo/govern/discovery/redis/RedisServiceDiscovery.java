package me.ahoo.govern.discovery.redis;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.govern.discovery.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
@Slf4j
public class RedisServiceDiscovery implements ServiceDiscovery {
    private final DiscoveryKeyGenerator keyGenerator;
    private final RedisClusterAsyncCommands<String, String> redisCommands;

    public RedisServiceDiscovery(DiscoveryKeyGenerator keyGenerator,
                                 RedisClusterAsyncCommands<String, String> redisCommands) {
        this.keyGenerator = keyGenerator;
        this.redisCommands = redisCommands;
    }

    @Override
    public CompletableFuture<List<ServiceInstance>> getInstances(String serviceId) {
        return DiscoveryRedisScripts.loadDiscoveryGetInstances(redisCommands)
                .thenCompose(sha -> {
                    RedisFuture<List<List<String>>> redisFuture = redisCommands.evalsha(sha, ScriptOutputType.MULTI, keyGenerator.getNamespace(), serviceId);
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
    public CompletableFuture<Set<String>> getServices() {
        var serviceIdxKey = keyGenerator.getServiceIdxKey();
        return redisCommands.smembers(serviceIdxKey).toCompletableFuture();
    }


    @Override
    public String getNamespace() {
        return keyGenerator.getNamespace();
    }
}
