package me.ahoo.govern.config.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.var;
import me.ahoo.govern.core.util.RedisScripts;
import org.junit.jupiter.api.*;

import java.util.Objects;

/**
 * @author ahoo wang
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BaseOnRedisClientTest {
    protected RedisClient redisClient;
    protected StatefulRedisConnection<String, String> redisConnection;

    @BeforeAll
    private void initRedis() {
        System.out.println("--- initRedis ---");
        redisClient = RedisClient.create("redis://localhost:6379");
        redisConnection = redisClient.connect();
    }

    protected void clearTestData(String namespace) {
        RedisScripts.clearTestData(namespace, redisConnection.async()).join();
    }


    @Test
    public void pingRedis() {
        System.out.println("--- pingRedis ---");
        var result = redisConnection.sync().ping();
        Assertions.assertEquals("PONG", result);
    }

    @AfterAll
    private void destroyRedis() {
        System.out.println("--- destroyRedis ---");

        if (Objects.nonNull(redisConnection)) {
            redisConnection.close();
        }
        if (Objects.nonNull(redisClient)) {
            redisClient.shutdown();
        }
    }
}
