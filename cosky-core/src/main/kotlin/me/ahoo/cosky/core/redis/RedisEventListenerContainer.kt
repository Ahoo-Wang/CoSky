/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
