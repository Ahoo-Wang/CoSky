package me.ahoo.cosky.discovery.redis

import me.ahoo.cosky.core.redis.RedisEventListenerContainer
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator.getNamespaceOfKey
import me.ahoo.cosky.discovery.ServiceEventListenerContainer
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.core.publisher.Flux

class RedisServiceEventListenerContainer(delegate: ReactiveRedisMessageListenerContainer) :
    ServiceEventListenerContainer, RedisEventListenerContainer<String, String>(delegate) {

    /**
     * @param topic namespace
     */
    override fun receiveEvent(topic: String): Flux<String> {
        val serviceIdxKey = DiscoveryKeyGenerator.getServiceIdxKey(topic)
        return delegate.receive(ChannelTopic.of(serviceIdxKey)).map {
            getNamespaceOfKey(it.channel)
        }
    }
}
