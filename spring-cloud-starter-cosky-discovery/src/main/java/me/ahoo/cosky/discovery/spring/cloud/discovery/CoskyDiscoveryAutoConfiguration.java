package me.ahoo.cosky.discovery.spring.cloud.discovery;

import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import me.ahoo.cosky.core.redis.RedisConnectionFactory;
import me.ahoo.cosky.core.listener.MessageListenable;
import me.ahoo.cosky.discovery.ServiceDiscovery;
import me.ahoo.cosky.discovery.loadbalancer.BinaryWeightRandomLoadBalancer;
import me.ahoo.cosky.discovery.loadbalancer.LoadBalancer;
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery;
import me.ahoo.cosky.discovery.redis.RedisServiceDiscovery;
import me.ahoo.cosky.discovery.redis.RedisServiceStatistic;
import me.ahoo.cosky.spring.cloud.CoskyAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.CommonsClientAutoConfiguration;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDiscoveryEnabled
@EnableConfigurationProperties({CoskyDiscoveryProperties.class})
@AutoConfigureAfter(CoskyAutoConfiguration.class)
@AutoConfigureBefore({CommonsClientAutoConfiguration.class})
public class CoskyDiscoveryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RedisServiceDiscovery redisServiceDiscovery(
            RedisConnectionFactory redisConnectionFactory) {
        RedisClusterAsyncCommands<String, String> redisCommands = redisConnectionFactory.getShareAsyncCommands();
        return new RedisServiceDiscovery(redisCommands);
    }

    @Bean
    @ConditionalOnMissingBean
    @Primary
    public ConsistencyRedisServiceDiscovery consistencyRedisServiceDiscovery(
            RedisServiceDiscovery redisServiceDiscovery,
            MessageListenable messageListenable) {
        return new ConsistencyRedisServiceDiscovery(redisServiceDiscovery, messageListenable);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisServiceStatistic redisServiceStatistic(
            RedisConnectionFactory redisConnectionFactory,
            MessageListenable messageListenable) {
        RedisClusterAsyncCommands<String, String> redisCommands = redisConnectionFactory.getShareAsyncCommands();
        return new RedisServiceStatistic(redisCommands, messageListenable);
    }

    @Bean
    @ConditionalOnMissingBean
    public CoskyDiscoveryClient governDiscoveryClient(
            ServiceDiscovery serviceDiscovery, CoskyDiscoveryProperties governDiscoveryProperties) {
        return new CoskyDiscoveryClient(serviceDiscovery, governDiscoveryProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public LoadBalancer loadBalancer(
            ConsistencyRedisServiceDiscovery serviceDiscovery) {
        return new BinaryWeightRandomLoadBalancer(serviceDiscovery);
    }
}
