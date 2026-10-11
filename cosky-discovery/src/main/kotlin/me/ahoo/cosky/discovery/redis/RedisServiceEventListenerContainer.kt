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
package me.ahoo.cosky.discovery.redis

import me.ahoo.cosky.core.redis.RedisEventListenerContainer
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator.getNamespaceOfKey
import me.ahoo.cosky.discovery.ServiceEventListenerContainer
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class RedisServiceEventListenerContainer(delegate: ReactiveRedisMessageListenerContainer) :
    ServiceEventListenerContainer, RedisEventListenerContainer<String, String>(delegate) {

    /**
     * @param topic namespace
     */
    override fun listenEvent(topic: String): Mono<Flux<String>> {
        val serviceIdxKey = DiscoveryKeyGenerator.getServiceIdxKey(topic)
        return delegate.receiveLater(ChannelTopic.of(serviceIdxKey)).map { messages ->
            messages.map { getNamespaceOfKey(it.channel) }
        }
    }
}
