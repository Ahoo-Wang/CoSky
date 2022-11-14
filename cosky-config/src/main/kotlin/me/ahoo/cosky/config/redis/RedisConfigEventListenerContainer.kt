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

class RedisConfigEventListenerContainer(delegate: ReactiveRedisMessageListenerContainer) :
    ConfigEventListenerContainer,
    RedisEventListenerContainer<NamespacedConfigId, ConfigChangedEvent>(delegate) {

    override fun receive(topic: NamespacedConfigId): Flux<ReactiveSubscription.Message<String, String>> {
        val topicStr: String = ConfigKeyGenerator.getConfigKey(topic.namespace, topic.configId)
        return delegate.receive(ChannelTopic.of(topicStr))
    }

    override fun asEvent(message: ReactiveSubscription.Message<String, String>): ConfigChangedEvent {
        val namespacedConfigId: NamespacedConfigId = ConfigKeyGenerator.getConfigIdOfKey(message.channel)
        val event = message.message.asConfigChangedEvent()
        return ConfigChangedEvent(namespacedConfigId, event)
    }
}
