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

package me.ahoo.cosky.core.redis;

import me.ahoo.cosky.core.test.AbstractReactiveRedisTest;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;

import java.util.concurrent.CountDownLatch;

/**
 * ReactiveRedisMessageListenerContainerTest .
 *
 * @author ahoo wang
 */
@Slf4j
@Disabled
public class ReactiveRedisMessageListenerContainerTest extends AbstractReactiveRedisTest {
    
    private ReactiveRedisMessageListenerContainer listenerContainer;
    
    @BeforeEach
    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        listenerContainer = new ReactiveRedisMessageListenerContainer(connectionFactory);
    }
    
    @AfterEach
    @Override
    public void destroy() {
        super.destroy();
    }
    
    @SneakyThrows
    @Test
    public void listenTo() {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        redisTemplate.listenTo(ChannelTopic.of("test"))
            .doOnNext(msg -> {
                log.warn(msg.getMessage());
            }).subscribe();
        redisTemplate.listenTo(ChannelTopic.of("test1"))
            .doOnNext(msg -> {
                log.warn(msg.getMessage());
            }).subscribe();
        countDownLatch.await();
    }
    
    @SneakyThrows
    @Test
    public void listenToC() {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        listenerContainer.receive(ChannelTopic.of("test"))
            .doOnNext(msg -> {
                log.warn(msg.getMessage());
            }).subscribe();
        listenerContainer.receive(ChannelTopic.of("test1"))
            .doOnNext(msg -> {
                log.warn(msg.getMessage());
            }).subscribe();
        countDownLatch.await();
    }
}
