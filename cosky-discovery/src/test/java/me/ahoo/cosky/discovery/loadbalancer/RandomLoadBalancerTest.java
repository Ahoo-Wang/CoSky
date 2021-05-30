package me.ahoo.cosky.discovery.loadbalancer;

import lombok.var;
import me.ahoo.cosky.discovery.BaseOnRedisClientTest;
import me.ahoo.cosky.discovery.RegistryProperties;
import me.ahoo.cosky.core.listener.RedisMessageListenable;
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery;
import me.ahoo.cosky.discovery.redis.RedisServiceDiscovery;
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * @author ahoo wang
 */
class RandomLoadBalancerTest extends BaseOnRedisClientTest {
    private final static String namespace = "test_lb";
    private RedisServiceDiscovery redisServiceDiscovery;
    private RedisServiceRegistry redisServiceRegistry;
    private RandomLoadBalancer randomLoadBalancer;

    @BeforeAll
    private void init() {
        var registryProperties = new RegistryProperties();
        redisServiceRegistry = new RedisServiceRegistry(registryProperties, redisConnection.async());
        redisServiceDiscovery = new RedisServiceDiscovery(redisConnection.async());
        var consistencyRedisServiceDiscovery = new ConsistencyRedisServiceDiscovery(redisServiceDiscovery, new RedisMessageListenable(redisClient.connectPubSub()));
        randomLoadBalancer = new RandomLoadBalancer(consistencyRedisServiceDiscovery);
    }

    @Test
    void chooseNone() {
        var instance = randomLoadBalancer.choose(namespace, UUID.randomUUID().toString()).join();
        Assertions.assertNull(instance);
    }

    @Test
    void chooseOne() {
        registerRandomInstanceFinal(namespace, redisServiceRegistry, instance -> {
            var expectedInstance = randomLoadBalancer.choose(namespace, instance.getServiceId()).join();
            Assertions.assertEquals(instance.getServiceId(), expectedInstance.getServiceId());
            Assertions.assertEquals(instance.getInstanceId(), expectedInstance.getInstanceId());
        });
    }

    @Test
    void chooseMultiple() {
        var serviceId = UUID.randomUUID().toString();
        var instance1 = createInstance(serviceId);
        var instance2 = createInstance(serviceId);
        var instance3 = createInstance(serviceId);
        redisServiceRegistry.register(namespace, instance1).join();
        redisServiceRegistry.register(namespace, instance2).join();
        redisServiceRegistry.register(namespace, instance3).join();
        var expectedInstance = randomLoadBalancer.choose(namespace, serviceId).join();
        Assertions.assertNotNull(expectedInstance);
        boolean succeeded = expectedInstance.getInstanceId().equals(instance1.getInstanceId())
                || expectedInstance.getInstanceId().equals(instance2.getInstanceId())
                || expectedInstance.getInstanceId().equals(instance3.getInstanceId());
        Assertions.assertTrue(succeeded);
        redisServiceRegistry.deregister(namespace, instance1);
        redisServiceRegistry.deregister(namespace, instance2);
        redisServiceRegistry.deregister(namespace, instance3);
    }
}
