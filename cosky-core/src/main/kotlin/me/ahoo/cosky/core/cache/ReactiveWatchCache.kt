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
package me.ahoo.cosky.core.cache

import io.github.oshai.kotlinlogging.KotlinLogging
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers
import reactor.util.retry.Retry
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [WatchCache] driven by a reactive event source.
 *
 * For every key the cache:
 * 1. subscribes to [listen] and waits until the subscription is active,
 * 2. then loads the initial value with [load], so no change can slip in between,
 * 3. then applies events one at a time with [apply], publishing a new [Snapshot] whenever the value changes.
 *
 * Failures are retried with [retry] by re-subscribing and reloading. While recovering, the last good snapshot keeps
 * being served; only a key that has never loaded fails its readers.
 *
 * @param listen emits the event stream of a key once its subscription is active.
 * @param load reads the current value of a key, empty when it does not exist.
 * @param apply derives the next value from an event. It must be idempotent and read the source's current state,
 * because an event may describe a change that the initial load already observed.
 * @param resyncInterval periodically reloads every key, a safety net for events lost while reconnecting.
 *
 * @author ahoo wang
 */
@Suppress("LongParameterList")
class ReactiveWatchCache<K : Any, V : Any, E : Any>(
    private val listen: (K) -> Mono<Flux<E>>,
    private val load: (K) -> Mono<V>,
    private val apply: (K, V?, E) -> Mono<V> = { key, _, _ -> load(key) },
    private val resyncInterval: Duration? = DEFAULT_RESYNC_INTERVAL,
    private val retry: Retry = DEFAULT_RETRY,
    private val scheduler: Scheduler = Schedulers.parallel()
) : WatchCache<K, V> {
    companion object {
        private val log = KotlinLogging.logger {}
        val DEFAULT_RESYNC_INTERVAL: Duration = Duration.ofMinutes(5)
        val DEFAULT_RETRY: Retry = Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
            .maxBackoff(Duration.ofSeconds(30))
    }

    private val entries = ConcurrentHashMap<K, Entry>()
    private val closed = AtomicBoolean(false)

    override fun get(key: K): Mono<V> {
        // Hot path: a loaded key is served without allocating.
        entries[key]?.currentValue?.let { return it }
        return entryOf(key).flatMap { it.current() }
    }

    override fun watch(key: K): Flux<Snapshot<V>> {
        return entryOf(key).flatMapMany { it.snapshots() }
    }

    private fun entryOf(key: K): Mono<Entry> {
        return Mono.fromCallable {
            check(!closed.get()) { "WatchCache is closed." }
            entries.computeIfAbsent(key) { Entry(it) }.also { it.start() }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        entries.values.forEach { it.close() }
        entries.clear()
    }

    private sealed interface State<out V : Any> {
        data class Loaded<V : Any>(val snapshot: Snapshot<V>) : State<V>
        data class Failed(val error: Throwable) : State<Nothing>
    }

    private sealed interface Signal<out E : Any> {
        data object Load : Signal<Nothing>
        data class Event<E : Any>(val event: E) : Signal<E>
    }

    private inner class Entry(private val key: K) {
        private val states = Sinks.many().replay().latest<State<V>>()
        private val started = AtomicBoolean(false)

        /**
         * Only written by the serialized pipeline.
         */
        @Volatile
        private var snapshot: Snapshot<V>? = null

        /**
         * The value of [snapshot], prebuilt so that reading it does not allocate.
         */
        @Volatile
        var currentValue: Mono<V>? = null
            private set

        @Volatile
        private var disposable: Disposable? = null

        fun start() {
            if (!started.compareAndSet(false, true)) {
                return
            }
            disposable = Mono.defer { listen(key) }
                .flatMapMany { events -> process(events) }
                .doOnError { onFailure(it) }
                .retryWhen(retry)
                .subscribe(
                    {},
                    { onTerminated(it) },
                    { onTerminated(null) },
                )
        }

        private fun process(events: Flux<E>): Flux<Unit> {
            val resync = resyncInterval?.let { interval ->
                Flux.interval(interval, interval, scheduler)
                    .onBackpressureDrop()
                    .map<Signal<E>> { Signal.Load }
            } ?: Flux.empty()
            // Subscribe to events before triggering the initial load, so that no change is missed.
            return Flux.merge(events.map<Signal<E>> { Signal.Event(it) }, Mono.just(Signal.Load), resync)
                .onBackpressureBuffer()
                .concatMap { signal ->
                    val next = when (signal) {
                        is Signal.Load -> load(key)
                        is Signal.Event -> apply(key, snapshot?.value, signal.event)
                    }
                    next.map { Value(it) }
                        .defaultIfEmpty(Value(null))
                        .doOnNext { publish(it.value) }
                        .thenReturn(Unit)
                }
        }

        private fun publish(value: V?) {
            val current = snapshot
            if (current != null && current.value == value) {
                return
            }
            val next = Snapshot((current?.version ?: 0) + 1, value)
            snapshot = next
            currentValue = Mono.justOrEmpty(value)
            states.emitNext(State.Loaded(next), Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(1)))
        }

        private fun onFailure(error: Throwable) {
            if (snapshot == null) {
                states.emitNext(State.Failed(error), Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(1)))
                return
            }
            log.warn(error) { "Watch key[$key] failed, serving the last snapshot while recovering." }
        }

        private fun onTerminated(error: Throwable?) {
            log.info { "Watch key[$key] terminated - [${error ?: "completed"}]." }
            entries.remove(key, this)
            states.emitComplete(Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(1)))
        }

        fun current(): Mono<V> {
            return states.asFlux()
                .next()
                .switchIfEmpty(Mono.error { IllegalStateException("Watch key[$key] terminated.") })
                .flatMap { state ->
                    when (state) {
                        is State.Loaded -> Mono.justOrEmpty(state.snapshot.value)
                        is State.Failed -> Mono.error(state.error)
                    }
                }
        }

        fun snapshots(): Flux<Snapshot<V>> {
            return states.asFlux().ofType(State.Loaded::class.java).map {
                @Suppress("UNCHECKED_CAST")
                (it as State.Loaded<V>).snapshot
            }
        }

        fun close() {
            disposable?.dispose()
            states.emitComplete(Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(1)))
        }
    }

    private class Value<V : Any>(val value: V?)
}
