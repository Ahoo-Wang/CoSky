package me.ahoo.cosky.discovery;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import me.ahoo.cosky.discovery.redis.RedisServiceDiscovery;
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry;
import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class RedisServiceDiscoveryBenchmark {
    private final static String namespace = "benchmark_svc_dvy";
    public ServiceDiscovery serviceDiscovery;
    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> redisConnection;

    @Setup
    public void setup() {
        System.out.println("\n ----- RedisServiceDiscoveryBenchmark setup ----- \n");
        redisClient = RedisClient.create("redis://localhost:6379");
        redisConnection = redisClient.connect();

        RegistryProperties registryProperties = new RegistryProperties();
        RedisServiceRegistry serviceRegistry = new RedisServiceRegistry(registryProperties, redisConnection.async());
        serviceRegistry.register(TestServiceInstance.TEST_FIXED_INSTANCE);
        serviceDiscovery = new RedisServiceDiscovery(redisConnection.async());
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
    }

    @Benchmark
    public Set<String> getServices() {
        return serviceDiscovery.getServices(namespace).join();
    }

    @Benchmark
    public List<ServiceInstance> getInstances() {
        return serviceDiscovery.getInstances(namespace, TestServiceInstance.TEST_FIXED_INSTANCE.getServiceId()).join();
    }
}
