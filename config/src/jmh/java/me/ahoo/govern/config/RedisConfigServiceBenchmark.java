package me.ahoo.govern.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import me.ahoo.govern.config.redis.RedisConfigService;
import org.openjdk.jmh.annotations.*;

import java.util.Objects;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class RedisConfigServiceBenchmark {

    public RedisConfigService configService;
    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> redisConnection;
    private String configId = this.getClass().getSimpleName();
    private String configData = "spring:\n" +
            "  application:\n" +
            "    name: govern-rest-api\n" +
            "  cloud:\n" +
            "    govern:\n" +
            "      namespace: dev\n" +
            "      config:\n" +
            "        config-id: ${spring.application.name}.yml\n" +
            "      redis:\n" +
            "        mode: standalone\n" +
            "        url: redis://localhost:6379\n";

    @Setup
    public void setup() {
        System.out.println("\n ----- RedisConfigBenchmark setup ----- \n");
        redisClient = RedisClient.create("redis://localhost:6379");
        redisConnection = redisClient.connect();
        ConfigKeyGenerator keyGenerator = new ConfigKeyGenerator("benchmark_cfg");
        configService = new RedisConfigService(keyGenerator, redisConnection.async());
        configService.setConfig(configId, configData);
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
    public void setConfig() {
        configService.setConfig(configId, configData).join();
    }

    @Benchmark
    public void getConfig() {
        configService.getConfig(configId).join();
    }

}
