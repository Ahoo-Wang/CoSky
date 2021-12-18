/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

package me.ahoo.cosky.rest.job;

import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosky.core.NamespaceService;
import me.ahoo.cosky.core.NamespacedContext;
import me.ahoo.cosky.discovery.ServiceStatistic;
import me.ahoo.simba.core.MutexContendServiceFactory;
import me.ahoo.simba.schedule.AbstractScheduler;
import me.ahoo.simba.schedule.ScheduleConfig;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * @author ahoo wang
 */
@Slf4j
@Service
public class StatServiceScheduler extends AbstractScheduler implements SmartLifecycle {
    public static final String STAT_MUTEX = "stat";

    private final NamespaceService namespaceService;
    private final ServiceStatistic serviceStatistic;

    public StatServiceScheduler(NamespaceService namespaceService, ServiceStatistic serviceStatistic, MutexContendServiceFactory contendServiceFactory) {
        super(STAT_MUTEX, ScheduleConfig.ofDelay(Duration.ofSeconds(1), Duration.ofSeconds(10)), contendServiceFactory);
        this.namespaceService = namespaceService;
        this.serviceStatistic = serviceStatistic;
    }

    @Override
    protected String getWorker() {
        return getClass().getSimpleName();
    }

    @Override
    protected void work() {
        if (log.isInfoEnabled()) {
            log.info("work - start.");
        }

        final String currentNamespace = NamespacedContext.GLOBAL.getNamespace();
        namespaceService.getNamespaces()
                .flatMapIterable(namespaces -> {
                    if (!namespaces.contains(currentNamespace)) {
                        namespaceService.setNamespace(currentNamespace).subscribe();
                    }
                    return namespaces;
                })
                .flatMap(serviceStatistic::statService)
                .doOnComplete(() -> log.info("doStatService - end."))
                .subscribe();
    }
}
