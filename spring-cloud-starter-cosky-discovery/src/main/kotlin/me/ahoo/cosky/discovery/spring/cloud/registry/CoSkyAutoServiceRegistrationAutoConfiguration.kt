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
import me.ahoo.cosky.discovery.ServiceInstance
import me.ahoo.cosky.discovery.ServiceRegistry
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry
import me.ahoo.cosky.discovery.spring.cloud.discovery.CoSkyDiscoveryAutoConfiguration
import me.ahoo.cosky.discovery.spring.cloud.discovery.ConditionalOnCoSkyDiscoveryEnabled
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationAutoConfiguration
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationProperties
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
    after = [CoSkyDiscoveryAutoConfiguration::class]
)
@ConditionalOnCoSkyDiscoveryEnabled
@EnableConfigurationProperties(
    CoSkyRegistryProperties::class
)
@ConditionalOnProperty(
    value = ["spring.cloud.service-registry.auto-registration.enabled"],
    matchIfMissing = true,
    havingValue = "true"
)
class CoSkyAutoServiceRegistrationAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun registryProperties(
        coSkyRegistryProperties: CoSkyRegistryProperties
    ): RegistryProperties {
        val registryProperties = RegistryProperties()
        registryProperties.setInstanceTtl(coSkyRegistryProperties.ttl)
        return registryProperties
    }

    @Bean
    @Primary
    fun redisServiceRegistry(
        registryProperties: RegistryProperties,
        redisTemplate: ReactiveStringRedisTemplate
    ): RedisServiceRegistry {
        return RedisServiceRegistry(registryProperties, redisTemplate)
    }

    @Bean
    fun renewInstanceService(
        coskyRegistryProperties: CoSkyRegistryProperties,
        redisServiceRegistry: RedisServiceRegistry
    ): RenewInstanceService {
        return RenewInstanceService(coskyRegistryProperties.renew, redisServiceRegistry)
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(CoSkyRegistration::class)
    fun coskyRegistration(
        context: ApplicationContext, properties: CoSkyRegistryProperties
    ): CoSkyRegistration {
        val serviceInstance = ServiceInstance()
        serviceInstance.setMetadata(properties.metadata)
        if (Strings.isNullOrEmpty(properties.serviceId)) {
            val serviceId: String = AppSupport.getAppName(context.environment)
            serviceInstance.setServiceId(serviceId)
        } else {
            serviceInstance.setServiceId(properties.serviceId)
        }
        if (!Strings.isNullOrEmpty(properties.schema)) {
            serviceInstance.setSchema(properties.schema)
        }
        if (!Strings.isNullOrEmpty(properties.host)) {
            serviceInstance.setHost(properties.host)
        }
        serviceInstance.setPort(properties.port)
        serviceInstance.setWeight(properties.weight)
        serviceInstance.setEphemeral(properties.isEphemeral)
        serviceInstance.setInstanceId(InstanceIdGenerator.DEFAULT.generate(serviceInstance))
        return CoSkyRegistration(serviceInstance)
    }

    @Bean
    @Primary
    fun coSkyServiceRegistry(
        serviceRegistry: ServiceRegistry,
        renewInstanceService: RenewInstanceService,
        coSkyRegistryProperties: CoSkyRegistryProperties
    ): CoSkyServiceRegistry {
        return CoSkyServiceRegistry(serviceRegistry, renewInstanceService, coSkyRegistryProperties)
    }

    @Bean
    @Primary
    fun coSkyAutoServiceRegistration(
        serviceRegistry: CoSkyServiceRegistry,
        registration: CoSkyRegistration,
        autoServiceRegistrationProperties: AutoServiceRegistrationProperties
    ): CoSkyAutoServiceRegistration {
        return CoSkyAutoServiceRegistration(serviceRegistry, registration, autoServiceRegistrationProperties)
    }

    @Bean
    fun coSkyAutoServiceRegistrationOfNoneWeb(
        serviceRegistry: CoSkyServiceRegistry,
        registration: CoSkyRegistration
    ): CoSkyAutoServiceRegistrationOfNoneWeb {
        return CoSkyAutoServiceRegistrationOfNoneWeb(serviceRegistry, registration)
    }
}
