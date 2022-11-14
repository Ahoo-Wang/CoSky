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
package me.ahoo.cosky.rest.stat

import me.ahoo.cosky.core.NamespaceService
import me.ahoo.cosky.core.NamespacedContext.namespace
import me.ahoo.cosky.discovery.ServiceStatistic
import me.ahoo.simba.core.MutexContendServiceFactory
import me.ahoo.simba.schedule.AbstractScheduler
import me.ahoo.simba.schedule.ScheduleConfig
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Service
import reactor.core.scheduler.Schedulers
import java.time.Duration

/**
 * Stat Service Scheduler.
 *
 * @author ahoo wang
 */
@Service
class StatServiceScheduler(
    private val namespaceService: NamespaceService,
    private val serviceStatistic: ServiceStatistic,
    contendServiceFactory: MutexContendServiceFactory
) : AbstractScheduler(
    STAT_MUTEX,
    ScheduleConfig.delay(Duration.ofSeconds(1), Duration.ofSeconds(10)),
    contendServiceFactory
),
    SmartLifecycle {

    companion object {
        private val log = LoggerFactory.getLogger(StatServiceScheduler::class.java)
        const val STAT_MUTEX = "stat"
    }

    override val worker: String
        get() = javaClass.simpleName

    override fun work() {
        if (log.isDebugEnabled) {
            log.debug("work - start.")
        }
        val currentNamespace = namespace
        namespaceService.namespaces
            .publishOn(Schedulers.boundedElastic())
            .collectList()
            .flatMapIterable {
                if (!it.contains(currentNamespace)) {
                    namespaceService.setNamespace(currentNamespace).subscribe()
                }
                it
            }
            .flatMap { namespace ->
                serviceStatistic.statService(
                    namespace
                )
            }
            .doOnComplete {
                if (log.isDebugEnabled) {
                    log.debug("work - end.")
                }
            }
            .subscribe()
    }

    override fun isRunning(): Boolean {
        return running
    }
}
