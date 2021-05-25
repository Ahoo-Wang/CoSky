package me.ahoo.govern.discovery;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.govern.core.listener.RedisMessageListenable;
import me.ahoo.govern.discovery.redis.ConsistencyRedisServiceDiscovery;
import me.ahoo.govern.discovery.redis.RedisServiceDiscovery;
import me.ahoo.govern.discovery.redis.RedisServiceRegistry;
import org.junit.jupiter.api.Assertions;
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

    private final static String namespace = "test_svc_csy";
    private ConsistencyRedisServiceDiscovery consistencyRedisServiceDiscovery;

    private ServiceInstance testInstance;
    private ServiceInstance testFixedInstance;
    private RedisServiceRegistry redisServiceRegistry;
    private RegistryProperties registryProperties;

    @BeforeEach
    private void init() {
        testInstance = TestServiceInstance.TEST_INSTANCE;
        testFixedInstance = TestServiceInstance.TEST_FIXED_INSTANCE;
        registryProperties = new RegistryProperties();
        registryProperties.setInstanceTtl(30);
        redisServiceRegistry = new RedisServiceRegistry(registryProperties, redisConnection.async());
        var redisServiceDiscovery = new RedisServiceDiscovery(redisConnection.async());
        consistencyRedisServiceDiscovery = new ConsistencyRedisServiceDiscovery(redisServiceDiscovery, new RedisMessageListenable(redisClient.connectPubSub()));
    }

    @Test
    public void getServices() {
        registerRandomInstanceFinal(namespace, redisServiceRegistry, (instance -> {
            var serviceIds = consistencyRedisServiceDiscovery.getServices(namespace).join();
            Assertions.assertNotNull(serviceIds);
            Assertions.assertTrue(serviceIds.contains(instance.getServiceId()));
        }));
    }

    @Test
    public void getInstances() {
        registerRandomInstanceFinal(namespace, redisServiceRegistry, (instance -> {
            var instances = consistencyRedisServiceDiscovery.getInstances(namespace, instance.getServiceId()).join();
            Assertions.assertNotNull(instances);

            var expectedInstance = instances.stream().findFirst().get();
            Assertions.assertNotNull(expectedInstance);
            Assertions.assertEquals(instance.getServiceId(), expectedInstance.getServiceId());
            Assertions.assertEquals(instance.getInstanceId(), expectedInstance.getInstanceId());
        }));
    }

    @Test
    public void getInstance() {
        registerRandomInstanceFinal(namespace, redisServiceRegistry, (instance -> {
            var actualInstance = consistencyRedisServiceDiscovery.getInstance(namespace, instance.getServiceId(), instance.getInstanceId()).join();
            Assertions.assertEquals(instance.getServiceId(), actualInstance.getServiceId());
            Assertions.assertEquals(instance.getInstanceId(), actualInstance.getInstanceId());
        }));
    }

    @Test
    public void getInstanceWithCache() {
        registerRandomInstanceFinal(namespace, redisServiceRegistry, (instance -> {
            consistencyRedisServiceDiscovery.getInstances(namespace, instance.getServiceId()).join();
            var actualInstance = consistencyRedisServiceDiscovery.getInstance(namespace, instance.getServiceId(), instance.getInstanceId()).join();
            Assertions.assertEquals(instance.getServiceId(), actualInstance.getServiceId());
            Assertions.assertEquals(instance.getInstanceId(), actualInstance.getInstanceId());

            var cachedInstance = consistencyRedisServiceDiscovery.getInstance(namespace, instance.getServiceId(), instance.getInstanceId()).join();
            Assertions.assertEquals(cachedInstance, actualInstance);
        }));
    }

    private final static int SLEEP_FOR_WAIT_MESSAGE = 1;

    @SneakyThrows
    protected void sleepForWaitNotify() {
        TimeUnit.SECONDS.sleep(SLEEP_FOR_WAIT_MESSAGE);
    }


    @Test
    public void getServicesListener() {
        clearTestData(namespace);
        var services = consistencyRedisServiceDiscovery.getServices(namespace);
        sleepForWaitNotify();
        redisServiceRegistry.register(namespace, testInstance).join();
        sleepForWaitNotify();
        services = consistencyRedisServiceDiscovery.getServices(namespace);
        Assertions.assertEquals(1, services.join().size());
        redisServiceRegistry.register(namespace, testFixedInstance).join();
        sleepForWaitNotify();
        services = consistencyRedisServiceDiscovery.getServices(namespace);
        Assertions.assertEquals(2, services.join().size());
    }


    @Test
    public void getInstancesListener() {
        clearTestData(namespace);
        var instances = consistencyRedisServiceDiscovery.getInstances(namespace, testInstance.getServiceId());
        sleepForWaitNotify();
        redisServiceRegistry.register(namespace, testInstance).join();
        sleepForWaitNotify();
        instances = consistencyRedisServiceDiscovery.getInstances(namespace, testInstance.getServiceId());
        Assertions.assertEquals(1, instances.join().size());
        redisServiceRegistry.deregister(namespace, testInstance).join();
        sleepForWaitNotify();
        instances = consistencyRedisServiceDiscovery.getInstances(namespace, testInstance.getServiceId());
        Assertions.assertEquals(0, instances.join().size());
    }

    @SneakyThrows
    @Test
    public void getInstancesListenerExpire() {
        clearTestData(namespace);
        var instances = consistencyRedisServiceDiscovery.getInstances(namespace, testInstance.getServiceId());
        sleepForWaitNotify();
        redisServiceRegistry.register(namespace, testInstance).join();
        sleepForWaitNotify();
        instances = consistencyRedisServiceDiscovery.getInstances(namespace, testInstance.getServiceId());
        Assertions.assertEquals(1, instances.join().size());
        TimeUnit.SECONDS.sleep(registryProperties.getInstanceTtl());
        instances = consistencyRedisServiceDiscovery.getInstances(namespace, testInstance.getServiceId());
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
            futures[i] = consistencyRedisServiceDiscovery.getServices(namespace);
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
