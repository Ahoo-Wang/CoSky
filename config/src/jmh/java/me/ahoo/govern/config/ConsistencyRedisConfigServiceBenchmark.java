package me.ahoo.govern.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import me.ahoo.govern.config.redis.ConsistencyRedisConfigService;
import me.ahoo.govern.config.redis.RedisConfigService;
import me.ahoo.govern.core.listener.MessageListenable;
import me.ahoo.govern.core.listener.RedisMessageListenable;
import org.openjdk.jmh.annotations.*;

import java.util.Objects;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class ConsistencyRedisConfigServiceBenchmark {
    public ConfigService configService;
    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> redisConnection;
    private MessageListenable messageListenable;
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
        System.out.println("\n ----- ConsistencyRedisConfigServiceBenchmark setup ----- \n");
        redisClient = RedisClient.create("redis://localhost:6379");
        redisConnection = redisClient.connect();
        ConfigKeyGenerator keyGenerator = new ConfigKeyGenerator("benchmark_csy_cfg");
        RedisConfigService redisConfigService = new RedisConfigService(keyGenerator, redisConnection.async());
        redisConfigService.setConfig(configId, configData);
        messageListenable = new RedisMessageListenable(redisClient.connectPubSub());
        configService = new ConsistencyRedisConfigService(keyGenerator, redisConfigService, messageListenable);
    }

    @TearDown
    public void tearDown() {
        System.out.println("\n ----- ConsistencyRedisConfigServiceBenchmark tearDown ----- \n");
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
    public void getConfig() {
        configService.getConfig(configId).join();
    }
}
