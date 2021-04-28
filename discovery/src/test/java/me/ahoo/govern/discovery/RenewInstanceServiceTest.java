package me.ahoo.govern.discovery;

import lombok.SneakyThrows;
import lombok.var;
import me.ahoo.govern.discovery.redis.RedisServiceRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

/**
 * @author ahoo wang
 */
public class RenewInstanceServiceTest extends BaseOnRedisClientTest {
    private final static String namespace = "test_renew";
    private ServiceInstance testInstance;
    private ServiceInstance testFixedInstance;
    private RedisServiceRegistry redisServiceRegistry;
    private RenewInstanceService renewService;

    @BeforeAll
    private void init() {
        testInstance = TestServiceInstance.TEST_INSTANCE;
        testFixedInstance = TestServiceInstance.TEST_FIXED_INSTANCE;
        var registryProperties = new RegistryProperties();
        registryProperties.setInstanceTtl(15);
        redisServiceRegistry = new RedisServiceRegistry(registryProperties, redisConnection.async());
        var renewProperties = new RenewProperties();
        renewService = new RenewInstanceService(renewProperties, redisServiceRegistry);
    }

    @SneakyThrows
    @Test
    public void start() {
        renewService.start();
        redisServiceRegistry.register(namespace, testInstance);
        redisServiceRegistry.register(namespace, testFixedInstance);
        TimeUnit.SECONDS.sleep(20);
    }

    @AfterAll
    public void stop() {
        renewService.stop();
    }

}
