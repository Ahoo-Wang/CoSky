package me.ahoo.cosky.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import me.ahoo.cosky.config.redis.RedisConfigService;
import org.openjdk.jmh.annotations.*;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class RedisConfigServiceBenchmark {
    private final static String namespace = "ben_cfg";
    public RedisConfigService configService;
    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> redisConnection;
    private String configId = this.getClass().getSimpleName();
    private String configData = "spring:\n" +
            "  application:\n" +
            "    name: cosky-rest-api\n" +
            "  cloud:\n" +
            "    cosky:\n" +
            "      namespace: dev\n" +
            "      config:\n" +
            "        config-id: ${spring.application.name}.yml\n" +
            "      redis:\n" +
            "        mode: standalone\n" +
            "        url: redis://localhost:6379\n";
    private AtomicInteger atomicInteger;

    @Setup
    public void setup() {
        System.out.println("\n ----- RedisConfigBenchmark setup ----- \n");
        redisClient = RedisClient.create("redis://localhost:6379");
        redisConnection = redisClient.connect();
        configService = new RedisConfigService(redisConnection.async());
        configService.setConfig(namespace, configId, configData);
        atomicInteger = new AtomicInteger();
    }

    @TearDown
    public void tearDown() {
        System.out.println("\n ----- RedisConfigBenchmark tearDown ----- \n");
        if (Objects.nonNull(redisConnection)) {
            redisConnection.close();
        }
        if (Objects.nonNull(redisClient)) {
            redisClient.shutdown();
        }
    }

    @Benchmark
    public Boolean setConfig() {
        String randomConfigId = String.valueOf(atomicInteger.incrementAndGet());
        return configService.setConfig(namespace, randomConfigId, configData).join();
    }

    @Benchmark
    public Config getConfig() {
        return configService.getConfig(namespace, configId).join();
    }

}
