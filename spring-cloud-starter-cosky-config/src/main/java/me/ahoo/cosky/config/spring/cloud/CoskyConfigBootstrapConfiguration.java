package me.ahoo.cosky.config.spring.cloud;

import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import lombok.var;
import me.ahoo.cosky.config.ConfigService;
import me.ahoo.cosky.config.redis.ConsistencyRedisConfigService;
import me.ahoo.cosky.config.redis.RedisConfigService;
import me.ahoo.cosky.core.redis.RedisConnectionFactory;
import me.ahoo.cosky.core.listener.MessageListenable;
import me.ahoo.cosky.spring.cloud.CoskyAutoConfiguration;
import me.ahoo.cosky.spring.cloud.support.AppSupport;
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
@ConditionalOnCoskyConfigEnabled
@EnableConfigurationProperties(CoskyConfigProperties.class)
@AutoConfigureAfter(CoskyAutoConfiguration.class)
public class CoskyConfigBootstrapConfiguration {

    public CoskyConfigBootstrapConfiguration(CoskyConfigProperties coskyConfigProperties, Environment environment) {
        var configId = coskyConfigProperties.getConfigId();
        if (Strings.isBlank(configId)) {
            configId = AppSupport.getAppName(environment) + "." + coskyConfigProperties.getFileExtension();
        }
        coskyConfigProperties.setConfigId(configId);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisConfigService redisConfigService(
            RedisConnectionFactory redisConnectionFactory) {
        RedisClusterAsyncCommands<String, String> redisCommands =redisConnectionFactory.getShareAsyncCommands();
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
    public CoskyPropertySourceLocator coskyPropertySourceLocator(CoskyConfigProperties configProperties, ConfigService configService) {
        return new CoskyPropertySourceLocator(configProperties, configService);
    }
}
