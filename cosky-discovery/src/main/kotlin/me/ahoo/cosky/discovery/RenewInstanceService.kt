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
package me.ahoo.cosky.discovery

import io.github.oshai.kotlinlogging.KotlinLogging
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

private object NoOpConsumeRenewInstance : Consumer<ServiceInstance> {
    override fun accept(t: ServiceInstance) = Unit
}

/**
 * Renew Instance Service.
 *
 * - A failing instance never stops the others from being renewed.
 * - A renew round still in flight causes the next tick to be skipped instead of overlapping.
 * - [stop] only cancels the schedule, so the service can be started again; [scheduler] is owned by the caller.
 * - After each round, expired instances of the renewed namespaces are reaped, so that `expired` events do not depend on
 *   any other component.
 *
 * @author ahoo wang
 */
class RenewInstanceService(
    private val renewProperties: RenewProperties,
    private val serviceRegistry: ServiceRegistry,
    private val scheduler: Scheduler = Schedulers.parallel(),
    private val hookOnRenew: Consumer<ServiceInstance> = NoOpConsumeRenewInstance
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val renewCounter = AtomicInteger()

    @Volatile
    private var scheduleDisposable: Disposable? = null

    val isRunning: Boolean
        get() = scheduleDisposable?.isDisposed == false

    @Synchronized
    fun start() {
        if (isRunning) {
            return
        }
        log.info { "Start." }
        scheduleDisposable = Flux.interval(renewProperties.initialDelay, renewProperties.period, scheduler)
            .onBackpressureDrop {
                log.warn { "Renew - previous round is still running, skip tick[$it]." }
            }
            .concatMap({ renew() }, 0)
            .subscribe()
    }

    @Synchronized
    fun stop() {
        if (!isRunning) {
            return
        }
        log.info { "Stop." }
        scheduleDisposable?.dispose()
    }

    private fun renew(): Mono<Void> {
        val times = renewCounter.incrementAndGet()
        val instances = serviceRegistry.registeredEphemeralInstances
        log.debug {
            "Renew - instances size:${instances.size} start - times@[$times]."
        }
        if (instances.isEmpty()) {
            return Mono.empty()
        }
        return Flux.fromIterable(instances.entries)
            .flatMap { (key, value) -> renew(key.namespace, value) }
            .doOnComplete {
                log.debug {
                    "Renew - instances size:${instances.size} end - times@[$times]."
                }
            }
            .then(reapExpiredInstances(instances.keys.mapTo(mutableSetOf()) { it.namespace }))
    }

    private fun reapExpiredInstances(namespaces: Set<String>): Mono<Void> {
        return Flux.fromIterable(namespaces)
            .flatMap { namespace ->
                Mono.defer { serviceRegistry.reapExpiredInstances(namespace) }
                    .timeout(renewProperties.period)
                    .doOnNext { reaped ->
                        if (reaped > 0) {
                            log.info { "Reap - [$reaped] expired instances @ namespace:[$namespace]." }
                        }
                    }
                    .onErrorResume {
                        log.warn(it) { "Reap - expired instances @ namespace:[$namespace] failed." }
                        Mono.empty()
                    }
            }
            .then()
    }

    private fun renew(namespace: String, instance: ServiceInstance): Mono<Boolean> {
        return Mono.defer { serviceRegistry.renew(namespace, instance) }
            .timeout(renewProperties.period)
            .doOnSuccess { hookOnRenew.accept(instance) }
            .onErrorResume {
                log.warn(it) {
                    "Renew - instanceId:[${instance.instanceId}] @ namespace:[$namespace] failed."
                }
                Mono.empty()
            }
    }
}
