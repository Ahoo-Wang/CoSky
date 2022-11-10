package me.ahoo.cosky.core.redis

import me.ahoo.cosky.core.EventListenerContainer
import org.springframework.data.redis.connection.ReactiveSubscription
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.core.publisher.Flux

abstract class RedisEventListenerContainer<T, E>(
    val delegate: ReactiveRedisMessageListenerContainer
) :
    EventListenerContainer<T, E> {
    override fun listen(topic: T): Flux<E> {
        return receive(topic)
            .map { asEvent(it) }
    }

    protected abstract fun receive(topic: T): Flux<ReactiveSubscription.Message<String, String>>

    protected abstract fun asEvent(message: ReactiveSubscription.Message<String, String>): E

    override fun close() {
        delegate.destroy()
    }
}
