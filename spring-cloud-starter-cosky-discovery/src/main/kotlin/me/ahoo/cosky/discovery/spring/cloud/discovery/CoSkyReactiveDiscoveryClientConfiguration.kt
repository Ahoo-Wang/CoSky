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
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled
import org.springframework.cloud.client.ConditionalOnReactiveDiscoveryEnabled
import org.springframework.cloud.client.ReactiveCommonsClientAutoConfiguration
import org.springframework.cloud.client.discovery.composite.reactive.ReactiveCompositeDiscoveryClientAutoConfiguration
import org.springframework.context.annotation.Bean

/**
 * CoSky Reactive Discovery Client Configuration.
 *
 * @author ahoo wang
 */
@AutoConfiguration(
    before = [ReactiveCommonsClientAutoConfiguration::class],
    after = [CoSkyDiscoveryAutoConfiguration::class]
)
@ConditionalOnCoSkyDiscoveryEnabled
@ConditionalOnDiscoveryEnabled
@ConditionalOnReactiveDiscoveryEnabled
class CoSkyReactiveDiscoveryClientConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun coSkyReactiveDiscoveryClient(serviceDiscovery: ServiceDiscovery): CoSkyReactiveDiscoveryClient {
        return CoSkyReactiveDiscoveryClient(serviceDiscovery)
    }
}
