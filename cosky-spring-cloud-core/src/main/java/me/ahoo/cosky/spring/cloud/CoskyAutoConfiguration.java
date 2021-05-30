package me.ahoo.cosky.spring.cloud;

import io.lettuce.core.AbstractRedisClient;
import me.ahoo.cosky.core.NamespaceService;
import me.ahoo.cosky.core.NamespacedContext;
import me.ahoo.cosky.core.listener.MessageListenable;
import me.ahoo.cosky.core.redis.RedisNamespaceService;
import me.ahoo.cosky.core.redis.RedisScriptInitializer;
import me.ahoo.cosky.spring.cloud.support.RedisClientSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCoskyEnabled
@EnableConfigurationProperties(CoskyProperties.class)
public class CoskyAutoConfiguration {

    public CoskyAutoConfiguration(CoskyProperties governProperties) {
        NamespacedContext.GLOBAL.setCurrentContextNamespace(governProperties.getNamespace());
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public AbstractRedisClient redisClient(CoskyProperties governProperties) {
        return RedisClientSupport.redisClient(governProperties.getRedis());
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisScriptInitializer redisScriptInitializer(AbstractRedisClient abstractRedisClient) {
        return new RedisScriptInitializer(abstractRedisClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageListenable messageListenable(AbstractRedisClient redisClient) {
        return RedisClientSupport.messageListenable(redisClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public NamespaceService namespaceService(AbstractRedisClient redisClient) {
        return new RedisNamespaceService(RedisClientSupport.getRedisCommands(redisClient));
    }
}
