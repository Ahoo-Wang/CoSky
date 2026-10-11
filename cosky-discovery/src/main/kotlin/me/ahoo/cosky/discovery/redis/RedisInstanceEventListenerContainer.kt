package me.ahoo.cosky.discovery.redis

import me.ahoo.cosky.core.redis.RedisEventListenerContainer
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator
import me.ahoo.cosky.discovery.Instance
import me.ahoo.cosky.discovery.Instance.Companion.asInstance
import me.ahoo.cosky.discovery.InstanceChangedEvent
import me.ahoo.cosky.discovery.InstanceChangedEvent.Companion.asServiceChangedEvent
import me.ahoo.cosky.discovery.InstanceEventListenerContainer
import me.ahoo.cosky.discovery.NamespacedServiceId
import org.springframework.data.redis.connection.ReactiveSubscription
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.core.publisher.Flux

class RedisInstanceEventListenerContainer(
    delegate: ReactiveRedisMessageListenerContainer
) :
    InstanceEventListenerContainer, RedisEventListenerContainer<NamespacedServiceId, InstanceChangedEvent>(delegate) {

    override fun receiveEvent(topic: NamespacedServiceId): Flux<InstanceChangedEvent> {
        val instancePattern = if (topic.serviceId.isNotBlank()) {
            DiscoveryKeyGenerator.getInstanceKeyPatternOfService(topic.namespace, topic.serviceId)
        } else {
            DiscoveryKeyGenerator.getInstanceKeyPatternOfNamespace(topic.namespace)
        }

        return delegate
            .receive(PatternTopic.of(instancePattern))
            .map {
                asEvent(it)
            }
    }

    private fun asEvent(message: ReactiveSubscription.Message<String, String>): InstanceChangedEvent {
        val namespace = DiscoveryKeyGenerator.getNamespaceOfKey(message.channel)
        val instanceId = DiscoveryKeyGenerator.getInstanceIdOfKey(namespace, message.channel)
        val instance: Instance = instanceId.asInstance()
        val serviceId = instance.serviceId
        val namespacedServiceId = NamespacedServiceId(namespace, serviceId)
        return InstanceChangedEvent(namespacedServiceId, message.message.asServiceChangedEvent(), instance)
    }
}
