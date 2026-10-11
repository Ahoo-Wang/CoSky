package me.ahoo.cosky.core

import reactor.core.publisher.Flux

/**
 * Event Listener Container.
 *
 * Does not own the underlying transport: whoever creates the transport is responsible for closing it.
 */
interface EventListenerContainer<T, E : Any> {
    fun receive(topic: T): Flux<E>
}
