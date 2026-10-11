package me.ahoo.cosky.core

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Event Listener Container.
 *
 * Does not own the underlying transport: whoever creates the transport is responsible for closing it.
 */
interface EventListenerContainer<T, E : Any> {
    /**
     * Emits the event stream of [topic] once the subscription is active, so that a read issued afterwards can not
     * miss a change.
     */
    fun listen(topic: T): Mono<Flux<E>>

    fun receive(topic: T): Flux<E> = listen(topic).flatMapMany { it }
}
