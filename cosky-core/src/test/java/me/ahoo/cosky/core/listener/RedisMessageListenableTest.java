package me.ahoo.cosky.core.listener;

import io.lettuce.core.RedisClient;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.cosky.core.TestRedisClient;
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
        redisEventListenerContainer = new RedisMessageListenable(redisClient.connectPubSub());
    }

    @Test
    public void addListener() throws InterruptedException {
        var patternTopic = PatternTopic.of("order_service@*");
        MessageListener messageListener = new MessageListener() {
            @Override
            public void onMessage(Topic topic, String channel, String message) {
                log.info("onEvent - [{}] topic:[{}] - channel:[{}] - message:[{}]", patternTopic.equals(topic), topic, channel, message);
            }
        };
        redisEventListenerContainer.addListener(patternTopic, messageListener).join();

    }

    @Test
    public void removeListener() throws InterruptedException {
        var patternTopic = PatternTopic.of("order_service@*");
        MessageListener messageListener = new MessageListener() {
            @Override
            public void onMessage(Topic topic, String channel, String message) {
                log.info("onEvent - [{}] topic:[{}] - channel:[{}] - message:[{}]", patternTopic.equals(topic), topic, channel, message);
            }
        };

        redisEventListenerContainer.removeListener(patternTopic, messageListener).join();
        redisEventListenerContainer.addListener(patternTopic, messageListener).join();

        redisEventListenerContainer.removeListener(patternTopic, messageListener).join();

        redisEventListenerContainer.addListener(patternTopic, messageListener).join();
        TimeUnit.SECONDS.sleep(2);
    }

    @Test
    public void concurrentAddListener() throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            new Thread(() -> {
                var patternTopic = PatternTopic.of("order_service@*");
                redisEventListenerContainer.addListener(patternTopic, (topic, channel, message) -> {
                    log.info("onEvent - [{}] topic:[{}] - channel:[{}] - message:[{}]", patternTopic.equals(topic), topic, channel, message);
                }).join();
            }).start();
        }
        TimeUnit.SECONDS.sleep(10);
    }

    @AfterAll
    private void destroy() {
        if (Objects.nonNull(redisClient)) {
            redisClient.shutdown();
        }

    }
}
