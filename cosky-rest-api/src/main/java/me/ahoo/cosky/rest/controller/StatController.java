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

package me.ahoo.cosky.rest.controller;

import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.cosky.config.ConfigService;
import me.ahoo.cosky.core.NamespaceService;
import me.ahoo.cosky.discovery.ServiceDiscovery;
import me.ahoo.cosky.discovery.ServiceStatistic;
import me.ahoo.cosky.rest.dto.service.GetStatResponse;
import me.ahoo.cosky.rest.support.RequestPathPrefix;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
@CrossOrigin("*")
@RestController
@RequestMapping(RequestPathPrefix.STAT_PREFIX)
@Slf4j
public class StatController {

    private final NamespaceService namespaceService;
    private final ConfigService configService;
    private final ServiceStatistic serviceStatistic;

    public StatController(NamespaceService namespaceService, ConfigService configService, ServiceStatistic serviceStatistic) {
        this.namespaceService = namespaceService;
        this.configService = configService;
        this.serviceStatistic = serviceStatistic;
    }

    @GetMapping
    public CompletableFuture<GetStatResponse> getStat(@PathVariable String namespace) {
        var getNamespacesFuture = namespaceService.getNamespaces();
        var getConfigsFuture = configService.getConfigs(namespace);
        var getServiceStatsFuture = serviceStatistic.getServiceStats(namespace);
        return CompletableFuture.allOf(getNamespacesFuture, getConfigsFuture, getServiceStatsFuture).thenApply((nil) -> {
            var statResponse = new GetStatResponse();
            statResponse.setNamespaces(getNamespacesFuture.join().size());
            statResponse.setConfigs(getConfigsFuture.join().size());
            GetStatResponse.Services services = new GetStatResponse.Services();
            var serviceStats = getServiceStatsFuture.join();
            services.setTotal(serviceStats.size());
            services.setHealth((int) serviceStats.stream().filter(stat -> stat.getInstanceCount() > 0).count());
            statResponse.setServices(services);
            var instances = serviceStats.stream().map(stat -> stat.getInstanceCount()).reduce(Integer.valueOf(0),
                    (left, right) -> left + right
            );
            statResponse.setInstances(instances);
            return statResponse;
        });
    }
}
