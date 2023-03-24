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
package me.ahoo.cosky.discovery.spring.cloud.registry

import me.ahoo.cosky.discovery.RegistryProperties
import me.ahoo.cosky.discovery.RenewInstanceService
import me.ahoo.cosky.discovery.ServiceRegistry
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry
import me.ahoo.cosky.spring.cloud.support.AppSupport
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationAutoConfiguration
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationProperties
import org.springframework.cloud.commons.util.InetUtils
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

/**
 * CoSky Auto Service Registration Auto Configuration.
 *
 * @author ahoo wang
 */
@AutoConfiguration(
    before = [AutoServiceRegistrationAutoConfiguration::class],
)
@ConditionalOnAutoRegistrationEnabled
@EnableConfigurationProperties(
    CoSkyRegistryProperties::class,
)
class CoSkyAutoServiceRegistrationAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun registryProperties(
        coSkyRegistryProperties: CoSkyRegistryProperties,
    ): RegistryProperties {
        return RegistryProperties(coSkyRegistryProperties.ttl)
    }

    @Bean
    @Primary
    fun redisServiceRegistry(
        registryProperties: RegistryProperties,
        redisTemplate: ReactiveStringRedisTemplate,
    ): RedisServiceRegistry {
        return RedisServiceRegistry(registryProperties, redisTemplate)
    }

    @Bean
    fun renewInstanceService(
        coSkyRegistryProperties: CoSkyRegistryProperties,
        redisServiceRegistry: RedisServiceRegistry,
    ): RenewInstanceService {
        return RenewInstanceService(coSkyRegistryProperties.renew, redisServiceRegistry)
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(CoSkyRegistration::class)
    fun coSkyRegistration(
        inetUtils: InetUtils,
        context: ApplicationContext,
        properties: CoSkyRegistryProperties,
    ): CoSkyRegistration {
        val serviceId = properties.serviceId.ifEmpty {
            AppSupport.getAppName(context.environment)
        }
        val host = properties.host.ifEmpty {
            val hostInfo = inetUtils.findFirstNonLoopbackHostInfo()
            hostInfo.ipAddress
        }
        return CoSkyRegistration(
            serviceId = serviceId,
            scheme = properties.schema,
            host = host,
            port = properties.port,
            weight = properties.weight,
            isEphemeral = properties.isEphemeral,
            metadata = properties.metadata,
        )
    }

    @Bean
    @Primary
    fun coSkyServiceRegistry(
        serviceRegistry: ServiceRegistry,
        renewInstanceService: RenewInstanceService,
        coSkyRegistryProperties: CoSkyRegistryProperties,
    ): CoSkyServiceRegistry {
        return CoSkyServiceRegistry(serviceRegistry, renewInstanceService, coSkyRegistryProperties)
    }

    @Bean
    @Primary
    fun coSkyAutoServiceRegistration(
        serviceRegistry: CoSkyServiceRegistry,
        registration: CoSkyRegistration,
        autoServiceRegistrationProperties: AutoServiceRegistrationProperties,
    ): CoSkyAutoServiceRegistration {
        return CoSkyAutoServiceRegistration(serviceRegistry, registration, autoServiceRegistrationProperties)
    }

    @Bean
    fun coSkyAutoServiceRegistrationOfNoneWeb(
        serviceRegistry: CoSkyServiceRegistry,
        registration: CoSkyRegistration,
    ): CoSkyAutoServiceRegistrationOfNoneWeb {
        return CoSkyAutoServiceRegistrationOfNoneWeb(serviceRegistry, registration)
    }
}
