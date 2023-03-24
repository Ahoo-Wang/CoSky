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
package me.ahoo.cosky.discovery.spring.cloud.discovery

import me.ahoo.cosky.discovery.ServiceDiscovery
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.cloud.client.CommonsClientAutoConfiguration
import org.springframework.cloud.client.ConditionalOnBlockingDiscoveryEnabled
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration
import org.springframework.context.annotation.Bean

/**
 * CoSky Discovery Client Configuration.
 *
 * @author ahoo wang
 */
@AutoConfiguration(
    before = [CommonsClientAutoConfiguration::class, SimpleDiscoveryClientAutoConfiguration::class],
    after = [CoSkyDiscoveryAutoConfiguration::class],
)
@ConditionalOnCoSkyDiscoveryEnabled
@ConditionalOnDiscoveryEnabled
@ConditionalOnBlockingDiscoveryEnabled
class CoSkyDiscoveryClientConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun coSkyDiscoveryClient(
        serviceDiscovery: ServiceDiscovery,
        coSkyDiscoveryProperties: CoSkyDiscoveryProperties,
    ): CoSkyDiscoveryClient {
        return CoSkyDiscoveryClient(serviceDiscovery, coSkyDiscoveryProperties)
    }
}
