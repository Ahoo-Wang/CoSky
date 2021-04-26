package me.ahoo.govern.discovery;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.govern.core.Consts;
import me.ahoo.govern.core.listener.RedisMessageListenable;
import me.ahoo.govern.discovery.redis.ConsistencyRedisServiceDiscovery;
import me.ahoo.govern.discovery.redis.RedisServiceDiscovery;
import me.ahoo.govern.discovery.redis.RedisServiceRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author ahoo wang
 */
@Slf4j
public class ConsistencyRedisServiceDiscoveryTest extends BaseOnRedisClientTest {

    private ConsistencyRedisServiceDiscovery consistencyRedisServiceDiscovery;

    private ServiceInstance testInstance;
    private ServiceInstance testFixedInstance;
    private RedisServiceRegistry redisServiceRegistry;
    private RegistryProperties registryProperties;

    @BeforeEach
    private void init() {
        this.namespace = "test_svc_csy";
        testInstance = TestServiceInstance.TEST_INSTANCE;
        testFixedInstance = TestServiceInstance.TEST_FIXED_INSTANCE;
        registryProperties = new RegistryProperties();
        registryProperties.setInstanceTtl(30);
        var keyGenerator = new DiscoveryKeyGenerator(namespace);
        redisServiceRegistry = new RedisServiceRegistry(registryProperties, keyGenerator, redisConnection.async());
        var redisServiceDiscovery = new RedisServiceDiscovery(keyGenerator, redisConnection.async());
        consistencyRedisServiceDiscovery = new ConsistencyRedisServiceDiscovery(keyGenerator, redisServiceDiscovery, new RedisMessageListenable(redisClient.connectPubSub()));
    }


    @Test
    public void getServices() {
        var serviceIds = consistencyRedisServiceDiscovery.getServices().join();
        Assertions.assertNotNull(serviceIds);
    }


    @Test
    public void getInstances() {
        var instances = consistencyRedisServiceDiscovery.getInstances(testInstance.getServiceId()).join();
        Assertions.assertNotNull(instances);
    }

    private final static int SLEEP_FOR_WAIT_MESSAGE = 1;

    @SneakyThrows
    protected void sleepForWaitNotify() {
        TimeUnit.SECONDS.sleep(SLEEP_FOR_WAIT_MESSAGE);
    }


    @Test
    public void getServicesListener() {
        clearTestData();
        var services = consistencyRedisServiceDiscovery.getServices();
        sleepForWaitNotify();
        redisServiceRegistry.register(testInstance).join();
        sleepForWaitNotify();
        services = consistencyRedisServiceDiscovery.getServices();
        Assertions.assertEquals(1, services.join().size());
        redisServiceRegistry.register(testFixedInstance).join();
        sleepForWaitNotify();
        services = consistencyRedisServiceDiscovery.getServices();
        Assertions.assertEquals(2, services.join().size());
    }


    @Test
    public void getInstancesListener() {
        clearTestData();
        var instances = consistencyRedisServiceDiscovery.getInstances(testInstance.getServiceId());
        sleepForWaitNotify();
        redisServiceRegistry.register(testInstance).join();
        sleepForWaitNotify();
        instances = consistencyRedisServiceDiscovery.getInstances(testInstance.getServiceId());
        Assertions.assertEquals(1, instances.join().size());
        redisServiceRegistry.deregister(testInstance).join();
        sleepForWaitNotify();
        instances = consistencyRedisServiceDiscovery.getInstances(testInstance.getServiceId());
        Assertions.assertEquals(0, instances.join().size());
    }

    @SneakyThrows
    @Test
    public void getInstancesListenerExpire() {
        clearTestData();
        var instances = consistencyRedisServiceDiscovery.getInstances(testInstance.getServiceId());
        sleepForWaitNotify();
        redisServiceRegistry.register(testInstance).join();
        sleepForWaitNotify();
        instances = consistencyRedisServiceDiscovery.getInstances(testInstance.getServiceId());
        Assertions.assertEquals(1, instances.join().size());
        TimeUnit.SECONDS.sleep(registryProperties.getInstanceTtl());
        instances = consistencyRedisServiceDiscovery.getInstances(testInstance.getServiceId());
        Assertions.assertEquals(0, instances.join().size());
    }


    private final static int REPEATED_SIZE = 60000;
    private final static int THREAD_COUNT = 5;

    //    @Test
    public void getInstancesRepeated() {
        for (int i = 0; i < REPEATED_SIZE; i++) {
            getInstances();
        }
    }


//    @Test
    public void getServicesRepeatedAsync() {
        var futures = new CompletableFuture[REPEATED_SIZE];
        for (int i = 0; i < REPEATED_SIZE; i++) {
            futures[i] = consistencyRedisServiceDiscovery.getServices();
        }
        CompletableFuture.allOf(futures).join();
    }

    //    @Test
    public void getInstancesRepeatedMMultiple() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(THREAD_COUNT);
        for (int thNum = 0; thNum < THREAD_COUNT; thNum++) {
            new Thread(() -> {
                getInstancesRepeated();
                countDownLatch.countDown();
            }).start();
        }
        countDownLatch.await();
    }

}
