package me.ahoo.govern.discovery;

import lombok.var;
import me.ahoo.govern.core.listener.RedisMessageListenable;
import me.ahoo.govern.discovery.redis.RedisServiceRegistry;
import me.ahoo.govern.discovery.redis.RedisServiceStatistic;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * @author ahoo wang
 */
public class RedisServiceStatisticTest extends BaseOnRedisClientTest {
    private final static String namespace = "test_svc_stat";
    private RedisServiceStatistic redisServiceStatistic;

    private RedisServiceRegistry redisServiceRegistry;

    @BeforeAll
    private void init() {
        var registryProperties = new RegistryProperties();
        redisServiceRegistry = new RedisServiceRegistry(registryProperties, redisConnection.async());
        redisServiceStatistic = new RedisServiceStatistic(redisConnection.async(), new RedisMessageListenable(redisClient.connectPubSub()));
    }

    @Test
    void statService() {
        var getServiceStatInstance = new ServiceInstance();
        getServiceStatInstance.setServiceId(UUID.randomUUID().toString());
        getServiceStatInstance.setSchema("http");
        getServiceStatInstance.setIp("127.0.0.1");
        getServiceStatInstance.setPort(8080);
        getServiceStatInstance.setInstanceId(InstanceIdGenerator.DEFAULT.generate(getServiceStatInstance));
        getServiceStatInstance.getMetadata().put("from", "test_getServiceStats");

        redisServiceRegistry.register(namespace, getServiceStatInstance).join();
        redisServiceStatistic.statService(namespace).join();

        var stats = redisServiceStatistic.getServiceStats(namespace).join();
        Assertions.assertTrue(stats.size() >= 1);
        var statOptional = stats.stream().filter(serviceStat -> serviceStat.getServiceId().equals(getServiceStatInstance.getServiceId())).findFirst();
        Assertions.assertTrue(statOptional.isPresent());
        var testStat = statOptional.get();
        Assertions.assertEquals(1, testStat.getInstanceCount());
    }
}
