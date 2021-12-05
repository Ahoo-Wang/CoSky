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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * @author ahoo wang
 */
@Slf4j
@Service
public class StatServiceJob {
    private final NamespaceService namespaceService;
    private final ServiceStatistic serviceStatistic;

    public StatServiceJob(NamespaceService namespaceService, ServiceStatistic serviceStatistic) {
        this.namespaceService = namespaceService;
        this.serviceStatistic = serviceStatistic;
    }

    @Scheduled(initialDelay = 10_000, fixedDelay = 60_000)
    public void doStatService() {
        if (log.isInfoEnabled()) {
            log.info("doStatService - start.");
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
