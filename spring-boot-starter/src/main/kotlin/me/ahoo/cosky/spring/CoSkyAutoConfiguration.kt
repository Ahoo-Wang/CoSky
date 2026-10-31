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

package me.ahoo.cosky.spring

import me.ahoo.cosky.client.CoSkyClient
import me.ahoo.cosky.spring.config.CoSkyConfigRefresher
import me.ahoo.cosky.spring.discovery.CoSkyDiscoveryClient
import me.ahoo.cosky.spring.discovery.CoSkyReactiveDiscoveryClient
import me.ahoo.cosky.spring.discovery.CoSkyServiceInstanceListSupplier
import me.ahoo.cosky.spring.registry.CoSkyAutoServiceRegistration
import me.ahoo.cosky.spring.registry.CoSkyRegistration
import me.ahoo.cosky.spring.registry.CoSkyServiceRegistry
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationConfiguration
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationProperties
import org.springframework.cloud.commons.util.InetUtils
import org.springframework.cloud.commons.util.UtilAutoConfiguration
import org.springframework.cloud.endpoint.event.RefreshEvent
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.env.Environment

/**
 * Everything a Spring application gets from CoSky (design §7.7). Each part applies only when its framework is on the
 * classpath.
 */
@AutoConfiguration(after = [UtilAutoConfiguration::class])
@ConditionalOnProperty(prefix = CoSkyProperties.PREFIX, name = ["enabled"], matchIfMissing = true)
@EnableConfigurationProperties(CoSkyProperties::class)
class CoSkyAutoConfiguration {
    private companion object {
        const val APPLICATION_NAME = "spring.application.name"
    }

    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean
    fun coSkyClient(properties: CoSkyProperties, environment: Environment): CoSkyClient =
        CoSkyClients.shared(properties, environment.getProperty(APPLICATION_NAME))

    @Bean
    fun coSkyClientCloser(properties: CoSkyProperties): DisposableBean = DisposableBean {
        CoSkyClients.close(properties)
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RefreshEvent::class)
    @ConditionalOnProperty(
        prefix = CoSkyProperties.PREFIX,
        name = ["config.refresh.enabled"],
        matchIfMissing = true,
    )
    class Refresh {
        @Bean
        fun coSkyConfigRefresher(
            client: CoSkyClient,
            properties: CoSkyProperties,
            publisher: ApplicationEventPublisher,
        ): CoSkyConfigRefresher = CoSkyConfigRefresher(client, properties, publisher)
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = CoSkyProperties.PREFIX, name = ["discovery.enabled"], matchIfMissing = true)
    class Discovery {
        @Bean
        fun coSkyDiscoveryClient(client: CoSkyClient, properties: CoSkyProperties): CoSkyDiscoveryClient =
            CoSkyDiscoveryClient(client, properties)

        @Bean
        fun coSkyReactiveDiscoveryClient(
            client: CoSkyClient,
            properties: CoSkyProperties,
        ): CoSkyReactiveDiscoveryClient = CoSkyReactiveDiscoveryClient(client, properties)
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ServiceInstanceListSupplier::class)
    @ConditionalOnProperty(prefix = CoSkyProperties.PREFIX, name = ["discovery.enabled"], matchIfMissing = true)
    @LoadBalancerClients(defaultConfiguration = [CoSkyLoadBalancing::class])
    class LoadBalancingImports

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = CoSkyProperties.PREFIX, name = ["registry.enabled"], matchIfMissing = true)
    @Import(AutoServiceRegistrationConfiguration::class)
    class Registry {
        @Bean
        fun coSkyServiceRegistry(client: CoSkyClient, properties: CoSkyProperties): CoSkyServiceRegistry =
            CoSkyServiceRegistry(client, properties)

        /**
         * Needs a web server for the port, and follows Spring Cloud's own switch, which also takes away its properties.
         */
        @Configuration(proxyBeanMethods = false)
        @ConditionalOnClass(WebServerApplicationContext::class)
        @ConditionalOnProperty(
            name = ["spring.cloud.service-registry.auto-registration.enabled"],
            matchIfMissing = true,
        )
        class AutoRegistration {
            @Bean
            fun coSkyAutoServiceRegistration(
                context: ApplicationContext,
                registry: CoSkyServiceRegistry,
                registrationProperties: AutoServiceRegistrationProperties,
                properties: CoSkyProperties,
                environment: Environment,
                inetUtils: InetUtils,
            ): CoSkyAutoServiceRegistration = CoSkyAutoServiceRegistration(
                context,
                registry,
                registrationProperties,
                CoSkyRegistration(
                    service = environment.getRequiredProperty("spring.application.name"),
                    host = inetUtils.findFirstNonLoopbackHostInfo().ipAddress,
                    secure = environment.getProperty("server.ssl.enabled", Boolean::class.java, false),
                    metadata = properties.registry.metadata,
                ),
            )
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(HealthIndicator::class)
    class Health {
        @Bean
        fun coSkyHealthIndicator(client: CoSkyClient): CoSkyHealthIndicator = CoSkyHealthIndicator(client)
    }
}

/**
 * Runs in the child context Spring Cloud LoadBalancer creates per service, whose name it puts in the environment. It
 * is neither nested in a configuration nor annotated, or the application context would pick it up too, with no name.
 */
class CoSkyLoadBalancing {
    @Bean
    fun coSkyServiceInstanceListSupplier(
        environment: Environment,
        client: CoSkyClient,
        properties: CoSkyProperties,
    ): ServiceInstanceListSupplier = CoSkyServiceInstanceListSupplier(
        checkNotNull(environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME)),
        client,
        properties,
    )
}
