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

import io.swagger.v3.oas.annotations.tags.Tag
import me.ahoo.cosky.config.ConfigService
import me.ahoo.cosky.core.NamespaceService
import me.ahoo.cosky.discovery.ServiceStat
import me.ahoo.cosky.discovery.ServiceStatistic
import me.ahoo.cosky.discovery.ServiceTopology
import me.ahoo.cosky.rest.support.RequestPathPrefix
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.util.stream.Collectors

/**
 * Stat Controller.
 *
 * @author ahoo wang
 */
@RestController
@RequestMapping(RequestPathPrefix.STAT_PREFIX)
@Tag(name = "Stat")
class StatController(
    private val namespaceService: NamespaceService,
    private val configService: ConfigService,
    private val serviceTopology: ServiceTopology,
    private val serviceStatistic: ServiceStatistic
) {
    @GetMapping
    fun getStat(@PathVariable namespace: String): Mono<GetStatResponse> {
        val getNamespacesFuture = namespaceService
            .namespaces
            .collect(Collectors.toSet())
        val getConfigsFuture = configService.getConfigs(
            namespace,
        ).collect(Collectors.toSet())
        val getServiceStatsFuture = serviceStatistic
            .getServiceStats(namespace)
            .collectList()
        return Mono.zip(getNamespacesFuture, getConfigsFuture, getServiceStatsFuture)
            .map {
                val statResponse = GetStatResponse()
                statResponse.namespaces = it.t1.size
                statResponse.configs = it.t2.size
                val services = GetStatResponse.Services()
                val serviceStats = it.t3
                services.total = serviceStats.size

                services.health =
                    serviceStats.count { (_, instanceCount) -> instanceCount > 0 }
                statResponse.services = services
                val instances = serviceStats.map(ServiceStat::instanceCount)
                    .reduceOrNull { acc, i -> acc + i } ?: 0
                statResponse.instances = instances
                statResponse
            }
    }

    @GetMapping("topology")
    fun getTopology(@PathVariable namespace: String): Mono<Map<String, Set<String>>> {
        return serviceTopology.getTopology(namespace)
    }
}
