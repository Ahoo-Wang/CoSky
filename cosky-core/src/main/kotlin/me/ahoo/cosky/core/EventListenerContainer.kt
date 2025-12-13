package me.ahoo.cosky.core

import reactor.core.publisher.Flux

interface EventListenerContainer<T, E : Any> : AutoCloseable {
    fun receive(topic: T): Flux<E>
}
