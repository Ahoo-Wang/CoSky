package me.ahoo.cosky.core.redis

import io.github.oshai.kotlinlogging.KotlinLogging
import me.ahoo.cosky.core.EventListenerContainer
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.util.concurrent.CancellationException

abstract class RedisEventListenerContainer<T, E : Any>(
    val delegate: ReactiveRedisMessageListenerContainer
) :
    EventListenerContainer<T, E> {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun receive(topic: T): Flux<E> {
        return receiveEvent(topic)
            .onErrorResume(CancellationException::class.java) {
                log.info {
                    "OnError $this - topic[$topic] is cancelled."
                }
                Flux.empty()
            }
            .doOnSubscribe {
                log.info {
                    "Receive $this - topic[$topic]."
                }
            }
    }

    protected abstract fun receiveEvent(topic: T): Flux<E>

    override fun close() {
        log.info {
            "Closing $this activeSubscriptions:[${delegate.activeSubscriptions.size}]"
        }
        delegate.destroyLater().subscribeOn(Schedulers.boundedElastic()).subscribe()
    }
}
