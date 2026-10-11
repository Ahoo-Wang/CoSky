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

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.kotlin.test.test
import reactor.util.retry.Retry
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class ReactiveWatchCacheTest {
    private val timeout = Duration.ofSeconds(5)
    private val fastRetry = Retry.fixedDelay(Long.MAX_VALUE, Duration.ofMillis(10))
    private val caches = CopyOnWriteArrayList<WatchCache<*, *>>()

    /**
     * Event source of one key: becomes "ready" (Redis confirmed SUBSCRIBE) only after [ready] is called.
     */
    private class FakeTopic {
        val events: Sinks.Many<String> = Sinks.many().multicast().directBestEffort()
        val subscribed = Sinks.one<Unit>()
        val listenTimes = AtomicInteger()

        fun listen(): Mono<Flux<String>> {
            listenTimes.incrementAndGet()
            return subscribed.asMono().thenReturn(events.asFlux())
        }

        fun ready() = subscribed.tryEmitValue(Unit)
        fun emit(event: String) = events.tryEmitNext(event)
    }

    private fun <V : Any> cache(
        listen: (String) -> Mono<Flux<String>>,
        load: (String) -> Mono<V>,
        apply: ((String, V?, String) -> Mono<V>)? = null,
        resyncInterval: Duration? = null
    ): ReactiveWatchCache<String, V, String> {
        val cache = if (apply == null) {
            ReactiveWatchCache(listen, load, resyncInterval = resyncInterval, retry = fastRetry)
        } else {
            ReactiveWatchCache(listen, load, apply, resyncInterval, fastRetry)
        }
        caches.add(cache)
        return cache
    }

    @AfterEach
    fun closeCaches() {
        caches.forEach { it.close() }
    }

    @Test
    fun getLoadsOnceAndCaches() {
        val topic = FakeTopic().apply { ready() }
        val loadTimes = AtomicInteger()
        val cache = cache({ topic.listen() }, { Mono.fromCallable { "v${loadTimes.incrementAndGet()}" } })

        cache.get("k").test().expectNext("v1").expectComplete().verify(timeout)
        cache.get("k").test().expectNext("v1").expectComplete().verify(timeout)

        loadTimes.get().assert().isEqualTo(1)
        topic.listenTimes.get().assert().isEqualTo(1)
    }

    @Test
    fun loadStartsOnlyAfterSubscriptionIsActive() {
        val topic = FakeTopic()
        val loadTimes = AtomicInteger()
        val cache = cache({ topic.listen() }, { Mono.fromCallable { "v${loadTimes.incrementAndGet()}" } })

        val result = cache.get("k").test()
        Thread.sleep(100)
        loadTimes.get().assert().isEqualTo(0)

        topic.ready()
        result.expectNext("v1").expectComplete().verify(timeout)
    }

    @Test
    fun eventPublishesNewSnapshot() {
        val topic = FakeTopic().apply { ready() }
        val loadTimes = AtomicInteger()
        val cache = cache({ topic.listen() }, { Mono.fromCallable { "v${loadTimes.incrementAndGet()}" } })

        cache.watch("k")
            .test()
            .expectNextMatches { it.version == 1L && it.value == "v1" }
            .then { topic.emit("changed") }
            .expectNextMatches { it.version == 2L && it.value == "v2" }
            .thenCancel()
            .verify(timeout)
        cache.get("k").test().expectNext("v2").expectComplete().verify(timeout)
    }

    @Test
    fun unchangedValueDoesNotBumpVersion() {
        val topic = FakeTopic().apply { ready() }
        val loadTimes = AtomicInteger()
        val cache = cache({ topic.listen() }, {
            Mono.fromCallable {
                loadTimes.incrementAndGet()
                "same"
            }
        })

        cache.watch("k")
            .test()
            .expectNextMatches { it.version == 1L }
            .then { topic.emit("changed") }
            .expectNoEvent(Duration.ofMillis(200))
            .thenCancel()
            .verify(timeout)
        loadTimes.get().assert().isEqualTo(2)
    }

    @Test
    fun eventsAreAppliedSeriallyInOrder() {
        val topic = FakeTopic().apply { ready() }
        val applied = CopyOnWriteArrayList<String>()
        val cache = cache<List<String>>(
            listen = { topic.listen() },
            load = { Mono.just(emptyList()) },
            apply = { _, current, event ->
                // Earlier events take longer: without serialization they would complete out of order.
                val delay = Duration.ofMillis(100L - event.toLong() * 10)
                Mono.delay(delay).map {
                    applied.add(event)
                    current.orEmpty() + event
                }
            },
        )
        cache.get("k").test().expectNext(emptyList()).expectComplete().verify(timeout)

        (1..5).forEach { topic.emit(it.toString()) }

        cache.watch("k")
            .filter { it.value?.size == 5 }
            .next()
            .test()
            .expectNextMatches { it.value == listOf("1", "2", "3", "4", "5") }
            .expectComplete()
            .verify(timeout)
        applied.assert().isEqualTo(listOf("1", "2", "3", "4", "5"))
    }

    @Test
    fun absentKey() {
        val topic = FakeTopic().apply { ready() }
        val cache = cache<String>({ topic.listen() }, { Mono.empty() })

        cache.get("k").test().expectComplete().verify(timeout)
        cache.watch("k").test().expectNextMatches { it.value == null }.thenCancel().verify(timeout)
    }

    @Test
    fun loadErrorIsNotCached() {
        val topic = FakeTopic().apply { ready() }
        val loadTimes = AtomicInteger()
        val cache = cache({ topic.listen() }, {
            Mono.fromCallable {
                check(loadTimes.incrementAndGet() > 1) { "transient failure" }
                "v"
            }
        })

        cache.get("k").test().expectError(IllegalStateException::class.java).verify(timeout)
        cache.watch("k").next().test().expectNextMatches { it.value == "v" }.expectComplete().verify(timeout)
        cache.get("k").test().expectNext("v").expectComplete().verify(timeout)
    }

    @Test
    fun serveStaleSnapshotWhileRecovering() {
        val topics = ConcurrentHashMap<Int, FakeTopic>()
        val listenTimes = AtomicInteger()
        val failing = Sinks.one<Unit>()
        val loadTimes = AtomicInteger()
        val cache = cache(
            listen = {
                topics.computeIfAbsent(
                    listenTimes.incrementAndGet()
                ) { FakeTopic().apply { ready() } }.listen()
            },
            load = {
                Mono.defer {
                    when (loadTimes.incrementAndGet()) {
                        1 -> Mono.just("v1")
                        2 -> Mono.error(IllegalStateException("redis down"))
                        // Recovery is held until the stale read has been asserted.
                        else -> failing.asMono().thenReturn("v3")
                    }
                }
            },
        )
        cache.get("k").test().expectNext("v1").expectComplete().verify(timeout)

        topics.getValue(1).emit("changed")
        Thread.sleep(100)
        cache.get("k").test().expectNext("v1").expectComplete().verify(timeout)

        failing.tryEmitValue(Unit)
        cache.watch("k").filter { it.value == "v3" }.next().test().expectNextCount(1).expectComplete().verify(timeout)
        listenTimes.get().assert().isGreaterThanOrEqualTo(2)
    }

    @Test
    fun resyncPeriodically() {
        val topic = FakeTopic().apply { ready() }
        val loadTimes = AtomicInteger()
        val cache = cache(
            listen = { topic.listen() },
            load = { Mono.fromCallable { "v${loadTimes.incrementAndGet()}" } },
            resyncInterval = Duration.ofMillis(50),
        )

        cache.watch("k").filter { it.value == "v3" }.next().test().expectNextCount(1).expectComplete().verify(timeout)
    }

    @Test
    fun listenCompletionEvictsEntry() {
        val topics = CopyOnWriteArrayList<FakeTopic>()
        val loadTimes = AtomicInteger()
        val cache = cache(
            listen = {
                FakeTopic().apply {
                    ready()
                    topics.add(this)
                }.listen()
            },
            load = { Mono.fromCallable { "v${loadTimes.incrementAndGet()}" } },
        )
        cache.get("k").test().expectNext("v1").expectComplete().verify(timeout)

        topics.single().events.tryEmitComplete()
        Thread.sleep(100)

        cache.get("k").test().expectNext("v2").expectComplete().verify(timeout)
        topics.size.assert().isEqualTo(2)
    }

    @Test
    fun closeCancelsSubscriptions() {
        val topic = FakeTopic().apply { ready() }
        val cache = cache({ topic.listen() }, { Mono.just("v") })
        cache.get("k").test().expectNext("v").expectComplete().verify(timeout)
        topic.events.currentSubscriberCount().assert().isEqualTo(1)

        cache.close()

        topic.events.currentSubscriberCount().assert().isEqualTo(0)
        cache.get("k").test().expectError(IllegalStateException::class.java).verify(timeout)
    }
}
