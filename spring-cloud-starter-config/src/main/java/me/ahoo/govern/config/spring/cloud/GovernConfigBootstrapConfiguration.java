package me.ahoo.govern.config.spring.cloud;

import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import lombok.var;
import me.ahoo.govern.config.ConfigService;
import me.ahoo.govern.config.redis.ConsistencyRedisConfigService;
import me.ahoo.govern.config.redis.RedisConfigService;
import me.ahoo.govern.core.listener.MessageListenable;
import me.ahoo.govern.spring.cloud.GovernAutoConfiguration;
import me.ahoo.govern.spring.cloud.support.AppSupport;
import me.ahoo.govern.spring.cloud.support.RedisClientSupport;
import org.apache.logging.log4j.util.Strings;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

/**
 * {@link org.springframework.cloud.util.PropertyUtils#BOOTSTRAP_ENABLED_PROPERTY}
 *
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnGovernConfigEnabled
@EnableConfigurationProperties(GovernConfigProperties.class)
@AutoConfigureAfter(GovernAutoConfiguration.class)
public class GovernConfigBootstrapConfiguration {

    public GovernConfigBootstrapConfiguration(GovernConfigProperties governConfigProperties, Environment environment) {
        var configId = governConfigProperties.getConfigId();
        if (Strings.isBlank(configId)) {
            configId = AppSupport.getAppName(environment) + "." + governConfigProperties.getFileExtension();
        }
        governConfigProperties.setConfigId(configId);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisConfigService redisConfigService(
            AbstractRedisClient redisClient) {
        RedisClusterAsyncCommands<String, String> redisCommands = RedisClientSupport.getRedisCommands(redisClient);
        return new RedisConfigService(redisCommands);
    }

    @Bean
    @ConditionalOnMissingBean
    @Primary
    public ConsistencyRedisConfigService consistencyRedisConfigService(
            RedisConfigService delegate, MessageListenable messageListenable) {
        return new ConsistencyRedisConfigService(delegate, messageListenable);
    }

    @Bean
    @ConditionalOnMissingBean
    public GovernPropertySourceLocator governPropertySourceLocator(GovernConfigProperties configProperties, ConfigService configService) {
        return new GovernPropertySourceLocator(configProperties, configService);
    }
}
