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

import org.slf4j.LoggerFactory
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
        private val log = LoggerFactory.getLogger(RenewInstanceService::class.java)
    }

    private val running = AtomicBoolean(false)
    private val renewCounter = AtomicInteger()
    private var scheduleDisposable: Disposable? = null

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }
        if (log.isInfoEnabled) {
            log.info("Start.")
        }
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
        if (log.isInfoEnabled) {
            log.info("Stop.")
        }
        scheduleDisposable?.dispose()
        scheduler.dispose()
    }

    private fun renew() {
        val times = renewCounter.incrementAndGet()
        val instances = serviceRegistry.registeredEphemeralInstances
        if (log.isDebugEnabled) {
            log.debug("Renew - instances size:{} start - times@[{}] .", instances.size, times)
        }
        if (instances.isEmpty()) {
            if (log.isDebugEnabled) {
                log.debug("Renew - instances size:{} end - times@[{}] .", instances.size, times)
            }
            return
        }
        Flux.fromIterable(instances.entries)
            .flatMap { (key, value) ->
                serviceRegistry.renew(key.namespace, value)
                    .doOnSuccess { hookOnRenew.accept(value) }
            }
            .doOnError {
                if (log.isWarnEnabled) {
                    log.warn("Renew - failed.", it)
                }
            }.doOnComplete {
                if (log.isDebugEnabled) {
                    log.debug(
                        "Renew - instances size:{} end - times@[{}].",
                        instances.size,
                        times,
                    )
                }
            }.subscribe()
    }
}
