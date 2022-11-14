package me.ahoo.cosky.core.redis

import me.ahoo.cosky.core.EventListenerContainer
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.ReactiveSubscription
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.util.concurrent.CancellationException

abstract class RedisEventListenerContainer<T, E>(
    val delegate: ReactiveRedisMessageListenerContainer
) :
    EventListenerContainer<T, E> {
    companion object {
        private val log = LoggerFactory.getLogger(RedisEventListenerContainer::class.java)
    }

    override fun listen(topic: T): Flux<E> {
        return receive(topic)
            .map { asEvent(it) }
            .onErrorResume(CancellationException::class.java) {
                if (log.isInfoEnabled) {
                    log.info("OnError {} - topic[{}] is cancelled.", this, topic)
                }
                Flux.empty()
            }
            .doOnSubscribe {
                if (log.isInfoEnabled) {
                    log.info("Listen {} - topic[{}].", this, topic)
                }
            }
    }

    protected abstract fun receive(topic: T): Flux<ReactiveSubscription.Message<String, String>>

    protected abstract fun asEvent(message: ReactiveSubscription.Message<String, String>): E

    override fun close() {
        if (log.isInfoEnabled) {
            log.info(
                "Closing {} activeSubscriptions:[{}]",
                this,
                delegate.activeSubscriptions.size
            )
        }
        delegate.destroyLater().subscribeOn(Schedulers.boundedElastic()).subscribe()
    }
}
