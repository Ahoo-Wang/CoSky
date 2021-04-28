package me.ahoo.govern.discovery;

import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.govern.discovery.redis.RedisServiceDiscovery;
import me.ahoo.govern.discovery.redis.RedisServiceRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/**
 * @author ahoo wang
 */
@Slf4j
public class RedisServiceDiscoveryTest extends BaseOnRedisClientTest {
    private final static String namespace = "test_svc";
    private RedisServiceDiscovery redisServiceDiscovery;

    private ServiceInstance serviceInstance;
    private RedisServiceRegistry redisServiceRegistry;

    @BeforeAll
    private void init() {
        serviceInstance = TestServiceInstance.TEST_INSTANCE;
        var registryProperties = new RegistryProperties();
        redisServiceRegistry = new RedisServiceRegistry(registryProperties, redisConnection.async());
        redisServiceDiscovery = new RedisServiceDiscovery(redisConnection.async());
    }

    private final static int REPEATED_SIZE = 60000;

    @Test
    public void getServices() {
        var serviceIds = redisServiceDiscovery.getServices(namespace).join();
        Assertions.assertNotNull(serviceIds);
    }

    @Test
    public void getInstances() {
        var instances = redisServiceDiscovery.getInstances(namespace, "test_fixed_service").join();
        Assertions.assertNotNull(instances);
    }


    //    @Test
    public void getServicesRepeatedAsync() {
        var futures = new CompletableFuture[REPEATED_SIZE];
        for (int i = 0; i < REPEATED_SIZE; i++) {
            futures[i] = redisServiceDiscovery.getServices();
        }
        CompletableFuture.allOf(futures).join();
    }

    //    @Test
    public void getInstancesRepeated() {
        for (int i = 0; i < 40000; i++) {
            getInstances();
        }
    }

    //    @Test
    public void getInstancesRepeatedMMultiple() throws InterruptedException {
        int threadCount = 50;
        CountDownLatch countDownLatch = new CountDownLatch(threadCount);
        for (int thNum = 0; thNum < threadCount; thNum++) {
            new Thread(() -> {
                getInstancesRepeated();
                countDownLatch.countDown();
            }).start();
        }
        countDownLatch.await();
    }

}
