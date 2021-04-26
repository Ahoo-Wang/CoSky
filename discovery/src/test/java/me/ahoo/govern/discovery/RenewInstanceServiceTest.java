package me.ahoo.govern.discovery;

import lombok.SneakyThrows;
import lombok.var;
import me.ahoo.govern.core.Consts;
import me.ahoo.govern.discovery.redis.RedisServiceRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

/**
 * @author ahoo wang
 */
public class RenewInstanceServiceTest extends BaseOnRedisClientTest {
    private ServiceInstance testInstance;
    private ServiceInstance testFixedInstance;
    private RedisServiceRegistry redisServiceRegistry;
    private RenewInstanceService renewService;

    @BeforeAll
    private void init() {
        this.namespace="test_renew";
        testInstance = TestServiceInstance.TEST_INSTANCE;
        testFixedInstance = TestServiceInstance.TEST_FIXED_INSTANCE;
        var registryProperties = new RegistryProperties();
        registryProperties.setInstanceTtl(15);
        var keyGenerator = new DiscoveryKeyGenerator(namespace);
        redisServiceRegistry = new RedisServiceRegistry(registryProperties, keyGenerator, redisConnection.async());
        var renewProperties = new RenewProperties();
        renewService = new RenewInstanceService(renewProperties, redisServiceRegistry);
    }

    @SneakyThrows
    @Test
    public void start() {
        renewService.start();
        redisServiceRegistry.register(testInstance);
        redisServiceRegistry.register(testFixedInstance);
        TimeUnit.SECONDS.sleep(20);
    }

    @AfterAll
    public void stop() {
        renewService.stop();
    }

}
