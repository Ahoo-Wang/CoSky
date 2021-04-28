package me.ahoo.govern.discovery;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import me.ahoo.govern.core.Consts;
import me.ahoo.govern.discovery.redis.RedisServiceRegistry;
import org.openjdk.jmh.annotations.*;

import java.util.Objects;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class RedisServiceRegistryBenchmark {
    private final static String namespace = "benchmark_svc";
    public ServiceRegistry serviceRegistry;
    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> redisConnection;

    @Setup
    public void setup() {
        System.out.println("\n ----- RedisServiceRegistryBenchmark setup ----- \n");
        redisClient = RedisClient.create("redis://localhost:6379");
        redisConnection = redisClient.connect();
        RegistryProperties registryProperties = new RegistryProperties();

        serviceRegistry = new RedisServiceRegistry(registryProperties, redisConnection.async());
        serviceRegistry.register(TestServiceInstance.TEST_FIXED_INSTANCE);
    }

    @TearDown
    public void tearDown() {
        System.out.println("\n ----- RedisServiceRegistryBenchmark tearDown ----- \n");
        if (Objects.nonNull(redisConnection)) {
            redisConnection.close();
        }
        if (Objects.nonNull(redisClient)) {
            redisClient.shutdown();
        }
    }

    @Benchmark
    public void register() {
        serviceRegistry.register(namespace, TestServiceInstance.TEST_INSTANCE).join();
    }

    @Benchmark
    public void deregister() {
        serviceRegistry.deregister(namespace, TestServiceInstance.TEST_INSTANCE).join();
    }

    @Benchmark
    public void renew() {
        serviceRegistry.renew(namespace, TestServiceInstance.TEST_INSTANCE).join();
    }

}
