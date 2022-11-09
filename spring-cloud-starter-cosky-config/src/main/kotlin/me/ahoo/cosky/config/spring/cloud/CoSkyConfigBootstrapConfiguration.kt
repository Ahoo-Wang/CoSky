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

import me.ahoo.cosky.config.ConfigService
import me.ahoo.cosky.config.redis.ConsistencyRedisConfigService
import me.ahoo.cosky.config.redis.RedisConfigService
import me.ahoo.cosky.spring.cloud.CoSkyAutoConfiguration
import me.ahoo.cosky.spring.cloud.support.AppSupport.getAppName
import org.apache.logging.log4j.util.Strings
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.env.Environment
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer

/**
 * CoSky Config Bootstrap Configuration.
 * [org.springframework.cloud.util.PropertyUtils.BOOTSTRAP_ENABLED_PROPERTY]
 *
 * @author ahoo wang
 */
@AutoConfiguration
@ConditionalOnCoSkyConfigEnabled
@EnableConfigurationProperties(
    CoSkyConfigProperties::class
)
@AutoConfigureAfter(CoSkyAutoConfiguration::class)
class CoSkyConfigBootstrapConfiguration(coSkyConfigProperties: CoSkyConfigProperties, environment: Environment) {
    init {
        var configId = coSkyConfigProperties.configId
        if (configId.isNullOrBlank()) {
            configId = getAppName(environment) + "." + coSkyConfigProperties.fileExtension
        }
        coSkyConfigProperties.configId = configId
    }

    @Bean
    @ConditionalOnMissingBean
    fun redisConfigService(redisTemplate: ReactiveStringRedisTemplate): RedisConfigService {
        return RedisConfigService(redisTemplate)
    }

    @Bean
    @ConditionalOnMissingBean
    @Primary
    fun consistencyRedisConfigService(
        delegate: RedisConfigService, listenerContainer: ReactiveRedisMessageListenerContainer
    ): ConsistencyRedisConfigService {
        return ConsistencyRedisConfigService(delegate, listenerContainer)
    }

    @Bean
    @ConditionalOnMissingBean
    fun coSkyPropertySourceLocator(
        configProperties: CoSkyConfigProperties,
        configService: ConfigService
    ): CoSkyPropertySourceLocator {
        return CoSkyPropertySourceLocator(configProperties, configService)
    }
}
