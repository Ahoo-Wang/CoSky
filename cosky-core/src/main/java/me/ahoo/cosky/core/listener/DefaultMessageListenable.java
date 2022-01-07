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

import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands;
import lombok.extern.slf4j.Slf4j;

/**
 * @author ahoo wang
 */
@Slf4j
public class DefaultMessageListenable implements MessageListenable {

    private final ChannelMessageSubscriber channelMessageSubscriber;
    private final PatternMessageSubscriber patternMessageSubscriber;

    public DefaultMessageListenable(RedisPubSubReactiveCommands<String, String> pubSubReactiveCommands) {
        this.channelMessageSubscriber = new ChannelMessageSubscriber(pubSubReactiveCommands);
        this.patternMessageSubscriber = new PatternMessageSubscriber(pubSubReactiveCommands);
    }

    @Override
    public void addChannelListener(String channel, MessageListener messageListener) {
        channelMessageSubscriber.addListener(channel, messageListener);
    }

    @Override
    public void removeChannelListener(String channel, MessageListener messageListener) {
        channelMessageSubscriber.removeListener(channel, messageListener);
    }

    @Override
    public void addPatternListener(String pattern, MessageListener messageListener) {
        patternMessageSubscriber.addListener(pattern, messageListener);
    }

    @Override
    public void removePatternListener(String pattern, MessageListener messageListener) {
        patternMessageSubscriber.removeListener(pattern, messageListener);
    }
}
