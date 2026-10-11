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
package me.ahoo.cosky.config.redis

import me.ahoo.cosky.config.ConfigChangedEvent
import me.ahoo.cosky.config.ConfigChangedEvent.Companion.asConfigChangedEvent
import me.ahoo.cosky.config.ConfigEventListenerContainer
import me.ahoo.cosky.config.ConfigKeyGenerator
import me.ahoo.cosky.config.NamespacedConfigId
import me.ahoo.cosky.core.redis.RedisEventListenerContainer
import org.springframework.data.redis.connection.ReactiveSubscription
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class RedisConfigEventListenerContainer(delegate: ReactiveRedisMessageListenerContainer) :
    ConfigEventListenerContainer,
    RedisEventListenerContainer<NamespacedConfigId, ConfigChangedEvent>(delegate) {

    override fun listenEvent(topic: NamespacedConfigId): Mono<Flux<ConfigChangedEvent>> {
        val topicStr: String = ConfigKeyGenerator.getConfigKey(topic.namespace, topic.configId)
        return delegate.receiveLater(ChannelTopic.of(topicStr)).map { messages ->
            messages.map { asEvent(it) }
        }
    }

    private fun asEvent(message: ReactiveSubscription.Message<String, String>): ConfigChangedEvent {
        val namespacedConfigId: NamespacedConfigId = ConfigKeyGenerator.getConfigIdOfKey(message.channel)
        val event = message.message.asConfigChangedEvent()
        return ConfigChangedEvent(namespacedConfigId, event)
    }
}
