package me.ahoo.cosky.core.redis

import io.github.oshai.kotlinlogging.KotlinLogging
import me.ahoo.cosky.core.EventListenerContainer
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.CancellationException

abstract class RedisEventListenerContainer<T, E : Any>(
    val delegate: ReactiveRedisMessageListenerContainer
) :
    EventListenerContainer<T, E> {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun listen(topic: T): Mono<Flux<E>> {
        return listenEvent(topic)
            .doOnSuccess {
                log.info {
                    "Listen $this - topic[$topic]."
                }
            }
            .map { events ->
                events.onErrorResume(CancellationException::class.java) {
                    log.info {
                        "OnError $this - topic[$topic] is cancelled."
                    }
                    Flux.empty()
                }
            }
    }

    /**
     * @see ReactiveRedisMessageListenerContainer.receiveLater
     */
    protected abstract fun listenEvent(topic: T): Mono<Flux<E>>
}
