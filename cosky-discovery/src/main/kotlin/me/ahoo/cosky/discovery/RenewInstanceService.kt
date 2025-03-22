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
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

private object NoOpConsumeRenewInstance : Consumer<ServiceInstance> {
    override fun accept(t: ServiceInstance) = Unit
}

/**
 * Renew Instance Service.
 *
 * @author ahoo wang
 */
class RenewInstanceService(
    private val renewProperties: RenewProperties,
    private val serviceRegistry: ServiceRegistry,
    private val scheduler: Scheduler = Schedulers.newSingle("CoSky-Renew", true),
    private val hookOnRenew: Consumer<ServiceInstance> = NoOpConsumeRenewInstance
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val running = AtomicBoolean(false)
    private val renewCounter = AtomicInteger()
    private var scheduleDisposable: Disposable? = null

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }
        log.info { "Start." }
        scheduleDisposable = scheduler.schedulePeriodically(
            { renew() },
            renewProperties.initialDelay.seconds,
            renewProperties.period.seconds,
            TimeUnit.SECONDS,
        )
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) {
            return
        }
        log.info { "Stop." }
        scheduleDisposable?.dispose()
        scheduler.dispose()
    }

    private fun renew() {
        val times = renewCounter.incrementAndGet()
        val instances = serviceRegistry.registeredEphemeralInstances
        log.debug {
            "Renew - instances size:${instances.size} start - times@[$times] ."
        }
        if (instances.isEmpty()) {
            log.debug {
                "Renew - instances size:${instances.size} end - times@[$times] ."
            }
            return
        }
        Flux.fromIterable(instances.entries)
            .flatMap { (key, value) ->
                serviceRegistry.renew(key.namespace, value)
                    .doOnSuccess { hookOnRenew.accept(value) }
            }
            .doOnError {
                log.warn {
                    "Renew - failed."
                }
            }.doOnComplete {
                log.debug {
                    "Renew - instances size:${instances.size} end - times@[$times]."
                }
            }.subscribe()
    }
}
