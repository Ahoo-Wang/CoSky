/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.ahoo.cosky.core.listener;

import io.lettuce.core.RedisClient;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.cosky.core.TestRedisClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author ahoo wang
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RedisMessageListenableTest {
    private DefaultMessageListenable messageListenable;
    private RedisClient redisClient;

    @BeforeAll
    private void init() {
        redisClient = TestRedisClient.createClient();
        messageListenable = new DefaultMessageListenable(redisClient.connectPubSub().reactive());
    }

    @Test
    public void addListener() throws InterruptedException {
        String patternTopic = "order_service@*";
        MessageListener messageListener = new MessageListener() {
            @Override
            public void onMessage(@Nullable String pattern, String channel, String message) {
                log.info("onEvent - [{}] pattern:[{}] - channel:[{}] - message:[{}]", patternTopic.equals(pattern), pattern, channel, message);
            }
        };
        messageListenable.addPatternListener(patternTopic, messageListener);

    }

    @Test
    public void removeListener() throws InterruptedException {
        String patternTopic = "order_service@*";
        MessageListener messageListener = new MessageListener() {
            @Override
            public void onMessage(@Nullable String pattern, String channel, String message) {
                log.info("onEvent - [{}] pattern:[{}] - channel:[{}] - message:[{}]", patternTopic.equals(pattern), pattern, channel, message);
            }
        };

        messageListenable.removePatternListener(patternTopic, messageListener);
        messageListenable.addPatternListener(patternTopic, messageListener);

        messageListenable.removePatternListener(patternTopic, messageListener);

        messageListenable.addPatternListener(patternTopic, messageListener);
        TimeUnit.SECONDS.sleep(2);
    }

    @Test
    public void concurrentAddListener() throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            new Thread(() -> {
                String patternTopic = "order_service@*";
                messageListenable.addPatternListener(patternTopic, (pattern, channel, message) -> {
                    log.info("onEvent - [{}] pattern:[{}] - channel:[{}] - message:[{}]", patternTopic.equals(pattern), pattern, channel, message);
                });
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
