package me.ahoo.cosky.discovery.redis

import me.ahoo.cosky.core.redis.RedisEventListenerContainer
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator.getNamespaceOfKey
import me.ahoo.cosky.discovery.ServiceEventListenerContainer
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.ReactiveSubscription
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.core.publisher.Flux

class RedisServiceEventListenerContainer(delegate: ReactiveRedisMessageListenerContainer) :
    ServiceEventListenerContainer, RedisEventListenerContainer<String, String>(delegate) {
    companion object {
        private val log = LoggerFactory.getLogger(RedisServiceEventListenerContainer::class.java)
    }

    /**
     * @param topic namespace
     */
    override fun receive(topic: String): Flux<ReactiveSubscription.Message<String, String>> {
        return Flux.defer {
            if (log.isDebugEnabled) {
                log.debug("Receive - topic:{}", topic)
            }
            val serviceIdxKey = DiscoveryKeyGenerator.getServiceIdxKey(topic)
            delegate.receive(ChannelTopic.of(serviceIdxKey))
        }
    }

    override fun asEvent(message: ReactiveSubscription.Message<String, String>): String {
        return getNamespaceOfKey(message.channel)
    }
}
