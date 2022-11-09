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
package me.ahoo.cosky.config.spring.cloud

import me.ahoo.cosky.config.ListenableConfigService
import me.ahoo.cosky.config.spring.cloud.refresh.CoSkyConfigRefresher
import me.ahoo.cosky.spring.cloud.CoSkyProperties
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

/**
 * CoSky Config Auto Configuration.
 *
 * @author ahoo wang
 */
@AutoConfiguration
@ConditionalOnCoSkyConfigEnabled
class CoSkyConfigAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun coSkyConfigRefresher(
        coSkyProperties: CoSkyProperties,
        configProperties: CoSkyConfigProperties,
        listenableConfigService: ListenableConfigService
    ): CoSkyConfigRefresher {
        return CoSkyConfigRefresher(coSkyProperties, configProperties, listenableConfigService)
    }
}
