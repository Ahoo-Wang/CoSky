package me.ahoo.govern.spring.cloud;

import io.lettuce.core.AbstractRedisClient;
import me.ahoo.govern.core.listener.MessageListenable;
import me.ahoo.govern.spring.cloud.support.RedisClientSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnGovernEnabled
@EnableConfigurationProperties(GovernProperties.class)
public class GovernAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public AbstractRedisClient redisClient(GovernProperties governProperties) {
       return RedisClientSupport.redisClient(governProperties.getRedis());
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageListenable messageListenable(AbstractRedisClient redisClient) {
        return RedisClientSupport.messageListenable(redisClient);
    }
}
