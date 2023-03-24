package me.ahoo.cosky.discovery.redis

import me.ahoo.cosky.core.redis.RedisEventListenerContainer
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator
import me.ahoo.cosky.discovery.Instance
import me.ahoo.cosky.discovery.Instance.Companion.asInstance
import me.ahoo.cosky.discovery.InstanceChangedEvent
import me.ahoo.cosky.discovery.InstanceChangedEvent.Companion.asServiceChangedEvent
import me.ahoo.cosky.discovery.InstanceEventListenerContainer
import me.ahoo.cosky.discovery.NamespacedServiceId
import me.ahoo.cosky.discovery.ServiceTopology
import org.springframework.data.redis.connection.ReactiveSubscription
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.core.publisher.Flux

class RedisInstanceEventListenerContainer(
    delegate: ReactiveRedisMessageListenerContainer,
    private val serviceTopology: ServiceTopology = ServiceTopology.NO_OP,
) :
    InstanceEventListenerContainer, RedisEventListenerContainer<NamespacedServiceId, InstanceChangedEvent>(delegate) {

    override fun receive(topic: NamespacedServiceId): Flux<ReactiveSubscription.Message<String, String>> {
        val instancePattern = if (topic.serviceId.isNotBlank()) {
            DiscoveryKeyGenerator.getInstanceKeyPatternOfService(topic.namespace, topic.serviceId)
        } else {
            DiscoveryKeyGenerator.getInstanceKeyPatternOfNamespace(topic.namespace)
        }

        @Suppress("UNCHECKED_CAST")
        return delegate
            .receive(PatternTopic.of(instancePattern))
            .doOnSubscribe {
                if (topic.serviceId.isNotBlank()) {
                    @Suppress("CallingSubscribeInNonBlockingScope")
                    serviceTopology.addTopology(topic.namespace, topic.serviceId).subscribe()
                }
            } as Flux<ReactiveSubscription.Message<String, String>>
    }

    override fun asEvent(message: ReactiveSubscription.Message<String, String>): InstanceChangedEvent {
        val namespace = DiscoveryKeyGenerator.getNamespaceOfKey(message.channel)
        val instanceId = DiscoveryKeyGenerator.getInstanceIdOfKey(namespace, message.channel)
        val instance: Instance = instanceId.asInstance()
        val serviceId = instance.serviceId
        val namespacedServiceId = NamespacedServiceId(namespace, serviceId)
        return InstanceChangedEvent(namespacedServiceId, message.message.asServiceChangedEvent(), instance)
    }
}
