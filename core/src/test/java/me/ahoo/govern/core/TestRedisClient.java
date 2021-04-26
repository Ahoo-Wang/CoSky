package me.ahoo.govern.core;

import io.lettuce.core.RedisClient;
import io.lettuce.core.cluster.api.sync.RedisClusterCommands;

/**
 * @author ahoo wang
 */
public final class TestRedisClient {

    public static RedisClient createClient() {
        return RedisClient.create("redis://localhost:6379");
    }

    public static RedisClusterCommands<String, String> createRedisClusterCommands(RedisClient redisClient) {
        return redisClient.connect().sync();
    }
}
