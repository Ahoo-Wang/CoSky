package me.ahoo.govern.discovery;

import lombok.SneakyThrows;
import lombok.var;
import me.ahoo.govern.core.Consts;
import me.ahoo.govern.discovery.redis.RedisServiceRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/**
 * @author ahoo wang
 */
public class RedisServiceRegistryTest extends BaseOnRedisClientTest {
    private ServiceInstance testInstance;
    private ServiceInstance testFixedInstance;
    private RedisServiceRegistry redisServiceRegistry;

    @BeforeAll
    private void init() {
        this.namespace="test_svc_rgy";
        testInstance = TestServiceInstance.TEST_INSTANCE;
        testFixedInstance = TestServiceInstance.TEST_FIXED_INSTANCE;
        var registryProperties = new RegistryProperties();
        registryProperties.setInstanceTtl(10);
        var keyGenerator = new DiscoveryKeyGenerator(namespace);
        redisServiceRegistry = new RedisServiceRegistry(registryProperties, keyGenerator, redisConnection.async());
    }

    @Test
    public void register() {
        clearTestData();
        var result = redisServiceRegistry.register(testInstance).join();
        Assertions.assertNotNull(result);
    }

    @Test
    public void renew() {
        var result = redisServiceRegistry.renew(testInstance).join();
        Assertions.assertNotNull(result);
    }

    @Test
    public void renewFixed() {
        var result = redisServiceRegistry.renew(testFixedInstance).join();
        Assertions.assertFalse(result);
    }

    @Test
    public void registerFixed() {
        var result = redisServiceRegistry.register(testFixedInstance).join();
        Assertions.assertNotNull(result);
    }


    @Test
    public void deregister() {
        redisServiceRegistry.deregister(testInstance).join();
    }

    private final static int REPEATED_SIZE = 60000;

    @SneakyThrows
//    @Test
    public void registerRepeatedSync() {
        for (int i = 0; i < REPEATED_SIZE; i++) {
            redisServiceRegistry.register(testInstance).join();
        }
    }

    @SneakyThrows
//    @Test
    public void registerRepeatedAsync() {
        var futures = new CompletableFuture[REPEATED_SIZE];
        for (int i = 0; i < REPEATED_SIZE; i++) {
            var future = redisServiceRegistry.register(testInstance);
            futures[i] = future;
        }
        CompletableFuture.allOf(futures).join();
    }

    private final static int THREAD_COUNT = 5;

//    @Test
    public void registerRepeatedMultiple() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(THREAD_COUNT);
        for (int thNum = 0; thNum < THREAD_COUNT; thNum++) {
            new Thread(() -> {
                registerRepeatedAsync();
                countDownLatch.countDown();
            }).start();
        }
        countDownLatch.await();
    }

//    @Test
    public void deregisterRepeatedAsync() {
        var futures = new CompletableFuture[REPEATED_SIZE];
        for (int i = 0; i < REPEATED_SIZE; i++) {
            futures[i] = redisServiceRegistry.deregister(testInstance);
        }
        CompletableFuture.allOf(futures).join();
    }

    //    @Test
    public void deregisterRepeatedMultiple() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(THREAD_COUNT);
        for (int thNum = 0; thNum < THREAD_COUNT; thNum++) {
            new Thread(() -> {
                deregisterRepeatedAsync();
                countDownLatch.countDown();
            }).start();
        }
        countDownLatch.await();
    }
}
