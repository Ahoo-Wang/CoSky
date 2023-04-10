package me.ahoo.cosky.core.redis

import me.ahoo.cosky.core.EventListenerContainer
import org.slf4j.LoggerFactory
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.util.concurrent.CancellationException

abstract class RedisEventListenerContainer<T, E>(
    val delegate: ReactiveRedisMessageListenerContainer,
) :
    EventListenerContainer<T, E> {
    companion object {
        private val log = LoggerFactory.getLogger(RedisEventListenerContainer::class.java)
    }

    override fun receive(topic: T): Flux<E> {
        return receiveEvent(topic)
            .onErrorResume(CancellationException::class.java) {
                if (log.isInfoEnabled) {
                    log.info("OnError {} - topic[{}] is cancelled.", this, topic)
                }
                Flux.empty()
            }
            .doOnSubscribe {
                if (log.isInfoEnabled) {
                    log.info("Receive {} - topic[{}].", this, topic)
                }
            }
    }

    protected abstract fun receiveEvent(topic: T): Flux<E>

    override fun close() {
        if (log.isInfoEnabled) {
            log.info(
                "Closing {} activeSubscriptions:[{}]",
                this,
                delegate.activeSubscriptions.size,
            )
        }
        delegate.destroyLater().subscribeOn(Schedulers.boundedElastic()).subscribe()
    }
}
