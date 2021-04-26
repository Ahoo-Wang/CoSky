package me.ahoo.govern.core.listener;

import io.lettuce.core.RedisClient;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.govern.core.TestRedisClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author ahoo wang
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RedisMessageListenableTest {
    private RedisMessageListenable redisEventListenerContainer;
    private RedisClient redisClient;

    @BeforeAll
    private void init() {
        redisClient = TestRedisClient.createClient();
        redisEventListenerContainer = new RedisMessageListenable( redisClient.connectPubSub());
    }

    @Test
    public void test() throws InterruptedException {
        var patternTopic = PatternTopic.of("order_service@*");

        redisEventListenerContainer.addListener(patternTopic, (topic, channel, message) -> {
            log.info("onEvent - [{}] topic:[{}] - channel:[{}] - message:[{}]", patternTopic.equals(topic), topic, channel, message);
        });
        TimeUnit.SECONDS.sleep(20);
    }

    @AfterAll
    private void destroy() {
        if (Objects.nonNull(redisClient)) {
            redisClient.shutdown();
        }

    }
}
