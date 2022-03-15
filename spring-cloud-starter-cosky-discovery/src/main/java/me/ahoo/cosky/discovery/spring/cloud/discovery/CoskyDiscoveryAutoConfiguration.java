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

package me.ahoo.cosky.discovery.spring.cloud.discovery;

import me.ahoo.cosky.discovery.loadbalancer.BinaryWeightRandomLoadBalancer;
import me.ahoo.cosky.discovery.loadbalancer.LoadBalancer;
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery;
import me.ahoo.cosky.discovery.redis.RedisServiceDiscovery;
import me.ahoo.cosky.discovery.redis.RedisServiceStatistic;
import me.ahoo.cosky.spring.cloud.CoskyAutoConfiguration;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;

/**
 * Cosky Discovery Auto Configuration.
 *
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDiscoveryEnabled
@EnableConfigurationProperties({CoskyDiscoveryProperties.class})
@AutoConfigureAfter(CoskyAutoConfiguration.class)
public class CoskyDiscoveryAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public RedisServiceDiscovery redisServiceDiscovery(ReactiveStringRedisTemplate redisTemplate) {
        return new RedisServiceDiscovery(redisTemplate);
    }
    
    @Bean
    @ConditionalOnMissingBean
    @Primary
    public ConsistencyRedisServiceDiscovery consistencyRedisServiceDiscovery(
        RedisServiceDiscovery redisServiceDiscovery,
        ReactiveStringRedisTemplate redisTemplate,
        ReactiveRedisMessageListenerContainer listenerContainer) {
        return new ConsistencyRedisServiceDiscovery(redisServiceDiscovery, redisTemplate, listenerContainer);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public RedisServiceStatistic redisServiceStatistic(
        ReactiveStringRedisTemplate redisTemplate,
        ReactiveRedisMessageListenerContainer listenerContainer) {
        return new RedisServiceStatistic(redisTemplate, listenerContainer);
    }
    
    
    @Bean
    @ConditionalOnMissingBean
    public LoadBalancer coskyLoadBalancer(
        ConsistencyRedisServiceDiscovery serviceDiscovery) {
        return new BinaryWeightRandomLoadBalancer(serviceDiscovery);
    }
}
