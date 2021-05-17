package me.ahoo.govern.core.redis;

import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import me.ahoo.govern.core.NamespaceService;
import me.ahoo.govern.core.Namespaced;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
public class RedisNamespaceService implements NamespaceService {

    private final static String NAMESPACE_IDX_KEY = Namespaced.SYSTEM + ":ns_idx";

    private final RedisClusterAsyncCommands<String, String> redisCommands;

    public RedisNamespaceService(RedisClusterAsyncCommands<String, String> redisCommands) {
        this.redisCommands = redisCommands;
    }

    @Override
    public CompletableFuture<Set<String>> getNamespaces() {
        return redisCommands.smembers(NAMESPACE_IDX_KEY).toCompletableFuture();
    }

    @Override
    public CompletableFuture<Boolean> setNamespace(String namespace) {
        return redisCommands.sadd(NAMESPACE_IDX_KEY, namespace)
                .thenApply(affected -> affected > 0)
                .toCompletableFuture();
    }

    @Override
    public CompletableFuture<Boolean> removeNamespace(String namespace) {
        return redisCommands.srem(NAMESPACE_IDX_KEY, namespace).thenApply(affected -> affected > 0).toCompletableFuture();
    }

}
