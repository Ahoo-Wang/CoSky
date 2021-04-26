package me.ahoo.govern.spring.cloud.support;

import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;

import me.ahoo.govern.core.RedisConfig;
import me.ahoo.govern.core.listener.MessageListenable;
import me.ahoo.govern.core.listener.RedisClusterMessageListenable;
import me.ahoo.govern.core.listener.RedisMessageListenable;

/**
 * @author ahoo wang
 */
public final class RedisClientSupport {
    private RedisClientSupport() {
    }

    public static AbstractRedisClient redisClient(RedisConfig redisConfig) {
        if (RedisConfig.RedisMode.CLUSTER.equals(redisConfig.getMode())) {
            return RedisClusterClient.create(redisConfig.getUrl());
        }
        return RedisClient.create(redisConfig.getUrl());
    }

    public static RedisClusterAsyncCommands<String, String> getRedisCommands(AbstractRedisClient redisClient) {
        if (redisClient instanceof RedisClusterClient) {
            return ((RedisClusterClient) redisClient).connect().async();
        } else {
            return ((RedisClient) redisClient).connect().async();
        }
    }

    public static MessageListenable messageListenable(AbstractRedisClient redisClient) {
        if (redisClient instanceof RedisClusterClient) {
            return new RedisClusterMessageListenable(((RedisClusterClient) redisClient).connectPubSub());
        }
        return new RedisMessageListenable(((RedisClient) redisClient).connectPubSub());
    }
}
