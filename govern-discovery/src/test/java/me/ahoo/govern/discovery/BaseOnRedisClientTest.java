package me.ahoo.govern.discovery;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.var;
import me.ahoo.govern.core.util.RedisScripts;
import org.junit.jupiter.api.*;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

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

    protected ServiceInstance createRandomInstance() {
        return createInstance(UUID.randomUUID().toString());
    }

    protected ServiceInstance createInstance(String serviceId) {
        var randomInstance = new ServiceInstance();
        randomInstance.setServiceId(serviceId);
        randomInstance.setSchema("http");
        randomInstance.setIp("127.0.0.1");
        randomInstance.setPort(ThreadLocalRandom.current().nextInt(65535));
        randomInstance.setInstanceId(InstanceIdGenerator.DEFAULT.generate(randomInstance));
        randomInstance.getMetadata().put("from", "test");
        return randomInstance;
    }


    protected void registerRandomInstanceFinal(String namespace, ServiceRegistry serviceRegistry, Consumer<ServiceInstance> doTest) {
        var randomInstance = createRandomInstance();
        serviceRegistry.register(namespace, randomInstance).join();
        doTest.accept(randomInstance);
        serviceRegistry.deregister(namespace, randomInstance).join();
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
