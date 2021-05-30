package me.ahoo.cosky.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import me.ahoo.cosky.config.redis.ConsistencyRedisConfigService;
import me.ahoo.cosky.config.redis.RedisConfigService;
import me.ahoo.cosky.core.listener.MessageListenable;
import me.ahoo.cosky.core.listener.RedisMessageListenable;
import org.openjdk.jmh.annotations.*;

import java.util.Objects;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class ConsistencyRedisConfigServiceBenchmark {
    private static final String namespace = "benchmark_csy_cfg";
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

        RedisConfigService redisConfigService = new RedisConfigService(redisConnection.async());
        redisConfigService.setConfig(configId, configData);
        messageListenable = new RedisMessageListenable(redisClient.connectPubSub());
        configService = new ConsistencyRedisConfigService(redisConfigService, messageListenable);
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
    public Config getConfig() {
        return configService.getConfig(namespace, configId).join();
    }
}
