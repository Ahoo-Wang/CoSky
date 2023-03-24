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

import me.ahoo.cosky.discovery.InstanceEventListenerContainer
import me.ahoo.cosky.discovery.ServiceEventListenerContainer
import me.ahoo.cosky.discovery.ServiceTopology
import me.ahoo.cosky.discovery.loadbalancer.BinaryWeightRandomLoadBalancer
import me.ahoo.cosky.discovery.loadbalancer.LoadBalancer
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery
import me.ahoo.cosky.discovery.redis.RedisInstanceEventListenerContainer
import me.ahoo.cosky.discovery.redis.RedisServiceDiscovery
import me.ahoo.cosky.discovery.redis.RedisServiceEventListenerContainer
import me.ahoo.cosky.discovery.redis.RedisServiceStatistic
import me.ahoo.cosky.discovery.redis.RedisServiceTopology
import me.ahoo.cosky.spring.cloud.CoSkyAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer

/**
 * CoSky Discovery Auto Configuration.
 *
 * @author ahoo wang
 */
@AutoConfiguration(after = [CoSkyAutoConfiguration::class])
@ConditionalOnDiscoveryEnabled
@EnableConfigurationProperties(
    CoSkyDiscoveryProperties::class,
)
class CoSkyDiscoveryAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun redisServiceDiscovery(redisTemplate: ReactiveStringRedisTemplate): RedisServiceDiscovery {
        return RedisServiceDiscovery(redisTemplate)
    }

    @Bean
    @ConditionalOnMissingBean
    fun redisServiceTopology(redisTemplate: ReactiveStringRedisTemplate): RedisServiceTopology {
        return RedisServiceTopology(redisTemplate)
    }

    @Bean
    @ConditionalOnMissingBean
    fun redisServiceEventListenerContainer(connectionFactory: ReactiveRedisConnectionFactory): ServiceEventListenerContainer {
        val listenerContainer = ReactiveRedisMessageListenerContainer(connectionFactory)
        return RedisServiceEventListenerContainer(listenerContainer)
    }

    @Bean
    @ConditionalOnMissingBean
    fun redisInstanceEventListenerContainer(
        connectionFactory: ReactiveRedisConnectionFactory,
        serviceTopology: ServiceTopology,
    ): InstanceEventListenerContainer {
        val listenerContainer = ReactiveRedisMessageListenerContainer(connectionFactory)
        return RedisInstanceEventListenerContainer(listenerContainer, serviceTopology)
    }

    @Bean
    @ConditionalOnMissingBean
    @Primary
    fun consistencyRedisServiceDiscovery(
        redisServiceDiscovery: RedisServiceDiscovery,
        serviceEventListenerContainer: ServiceEventListenerContainer,
        instanceEventListenerContainer: InstanceEventListenerContainer,
    ): ConsistencyRedisServiceDiscovery {
        return ConsistencyRedisServiceDiscovery(
            delegate = redisServiceDiscovery,
            serviceEventListenerContainer = serviceEventListenerContainer,
            instanceEventListenerContainer = instanceEventListenerContainer,
        )
    }

    @Bean
    @ConditionalOnMissingBean
    fun redisServiceStatistic(
        redisTemplate: ReactiveStringRedisTemplate,
        instanceEventListenerContainer: InstanceEventListenerContainer,
    ): RedisServiceStatistic {
        return RedisServiceStatistic(redisTemplate, instanceEventListenerContainer)
    }

    @Bean
    @ConditionalOnMissingBean
    fun coSkyLoadBalancer(
        serviceDiscovery: ConsistencyRedisServiceDiscovery,
        instanceEventListenerContainer: InstanceEventListenerContainer,
    ): LoadBalancer {
        return BinaryWeightRandomLoadBalancer(serviceDiscovery, instanceEventListenerContainer)
    }
}
