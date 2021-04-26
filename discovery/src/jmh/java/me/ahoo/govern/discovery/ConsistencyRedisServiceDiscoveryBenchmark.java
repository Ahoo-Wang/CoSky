package me.ahoo.govern.discovery;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import me.ahoo.govern.core.listener.MessageListenable;
import me.ahoo.govern.core.listener.RedisMessageListenable;
import me.ahoo.govern.discovery.redis.ConsistencyRedisServiceDiscovery;
import me.ahoo.govern.discovery.redis.RedisServiceDiscovery;
import me.ahoo.govern.discovery.redis.RedisServiceRegistry;
import org.openjdk.jmh.annotations.*;

import java.util.Objects;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class ConsistencyRedisServiceDiscoveryBenchmark {
    public ServiceDiscovery serviceDiscovery;
    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> redisConnection;
    private MessageListenable messageListenable;

    @Setup
    public void setup() {
        System.out.println("\n ----- RedisServiceDiscoveryBenchmark setup ----- \n");
        redisClient = RedisClient.create("redis://localhost:6379");
        redisConnection = redisClient.connect();
        DiscoveryKeyGenerator keyGenerator = new DiscoveryKeyGenerator("benchmark_csy_svc_dvy");
        RegistryProperties registryProperties = new RegistryProperties();
        RedisServiceRegistry serviceRegistry = new RedisServiceRegistry(registryProperties, keyGenerator, redisConnection.async());
        serviceRegistry.register(TestServiceInstance.TEST_FIXED_INSTANCE);
        RedisServiceDiscovery redisServiceDiscovery = new RedisServiceDiscovery(keyGenerator, redisConnection.async());
        messageListenable = new RedisMessageListenable(redisClient.connectPubSub());
        serviceDiscovery = new ConsistencyRedisServiceDiscovery(keyGenerator, redisServiceDiscovery, messageListenable);
    }

    @TearDown
    public void tearDown() {
        System.out.println("\n ----- RedisServiceDiscoveryBenchmark tearDown ----- \n");
        if (Objects.nonNull(redisConnection)) {
            redisConnection.close();
        }
        if (Objects.nonNull(redisClient)) {
            redisClient.shutdown();
        }
        if (Objects.nonNull(messageListenable)) {
            try {
                messageListenable.close();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }
    }

    @Benchmark
    public void getServices() {
        serviceDiscovery.getServices().join();
    }

    @Benchmark
    public void getInstances() {
        serviceDiscovery.getInstances(TestServiceInstance.TEST_FIXED_INSTANCE.getServiceId()).join();
    }
}
