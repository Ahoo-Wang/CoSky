/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
@Slf4j
public class RedisMessageListenable extends AbstractMessageListenable {

    private final StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private final RedisPubSubAsyncCommands<String, String> pubSubCommands;
    private final RedisPubSubListenerAdapter listenerAdapter;

    public RedisMessageListenable(StatefulRedisPubSubConnection<String, String> pubSubConnection) {
        this.pubSubConnection = pubSubConnection;
        this.pubSubCommands = pubSubConnection.async();
        this.listenerAdapter = new RedisPubSubListenerAdapter();
        this.pubSubConnection.addListener(listenerAdapter);
    }

    @Override
    public void close() throws Exception {
        pubSubConnection.close();
    }

    @Override
    protected CompletableFuture<Void> subscribe(ChannelTopic channelTopic) {
        return pubSubCommands.subscribe(channelTopic.getTopic()).toCompletableFuture();
    }

    @Override
    protected CompletableFuture<Void> subscribe(PatternTopic patternTopic) {
        return pubSubCommands.psubscribe(patternTopic.getTopic()).toCompletableFuture();
    }

    @Override
    protected CompletableFuture<Void> unsubscribe(ChannelTopic topic) {
        return pubSubCommands.unsubscribe(topic.getTopic()).toCompletableFuture();
    }

    @Override
    protected CompletableFuture<Void> unsubscribe(PatternTopic topic) {
        return pubSubCommands.punsubscribe(topic.getTopic()).toCompletableFuture();
    }

    private class RedisPubSubListenerAdapter implements RedisPubSubListener<String, String> {
        /**
         * Message received from a channel subscription.
         *
         * @param channel Channel.
         * @param message Message.
         */
        @Override
        public void message(String channel, String message) {
            if (log.isDebugEnabled()){
                log.debug("Message received from a channel subscription - channel[{}] | message[{}]", channel, message);
            }
            onMessage(channel, message, null);
        }

        /**
         * Message received from a pattern subscription.
         *
         * @param pattern Pattern
         * @param channel Channel
         * @param message Message
         */
        @Override
        public void message(String pattern, String channel, String message) {
            if (log.isDebugEnabled()){
                log.debug("Message received from a pattern subscription - pattern[{}] | channel[{}] | message[{}]", pattern, channel, message);
            }
            onMessage(channel, message, pattern);
        }

        /**
         * Subscribed to a channel.
         *
         * @param channel Channel
         * @param count   Subscription count.
         */
        @Override
        public void subscribed(String channel, long count) {
            if (log.isInfoEnabled()){
                log.info("Subscribed to a channel - channel[{}] | [{}]", channel, count);
            }
        }

        /**
         * Subscribed to a pattern.
         *
         * @param pattern Pattern.
         * @param count   Subscription count.
         */
        @Override
        public void psubscribed(String pattern, long count) {
            if (log.isInfoEnabled()){
                log.info("PSubscribed to a pattern - pattern[{}] | [{}]", pattern, count);
            }
        }

        /**
         * Unsubscribed from a channel.
         *
         * @param channel Channel
         * @param count   Subscription count.
         */
        @Override
        public void unsubscribed(String channel, long count) {
            if (log.isInfoEnabled()){
                log.info("Unsubscribed from a channel - channel[{}] | [{}]", channel, count);
            }
        }

        /**
         * Unsubscribed from a pattern.
         *
         * @param pattern Channel
         * @param count   Subscription count.
         */
        @Override
        public void punsubscribed(String pattern, long count) {
            if (log.isInfoEnabled()){
                log.info("PUnsubscribed from a pattern - pattern[{}] | [{}]", pattern, count);
            }
        }
    }
}
