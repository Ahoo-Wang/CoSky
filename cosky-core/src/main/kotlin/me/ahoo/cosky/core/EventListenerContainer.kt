package me.ahoo.cosky.core

import reactor.core.publisher.Flux

interface EventListenerContainer<T, E> : AutoCloseable {
    fun listen(topic: T): Flux<E>
}
