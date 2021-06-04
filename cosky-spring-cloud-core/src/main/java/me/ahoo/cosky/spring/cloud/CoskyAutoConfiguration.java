package me.ahoo.cosky.spring.cloud;

import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import me.ahoo.cosky.core.NamespaceService;
import me.ahoo.cosky.core.NamespacedContext;
import me.ahoo.cosky.core.redis.RedisConnectionFactory;
import me.ahoo.cosky.core.listener.MessageListenable;
import me.ahoo.cosky.core.redis.RedisNamespaceService;
import me.ahoo.cosky.core.redis.RedisScriptInitializer;
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

    public CoskyAutoConfiguration(CoskyProperties coskyProperties) {
        NamespacedContext.GLOBAL.setCurrentContextNamespace(coskyProperties.getNamespace());
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(ClientResources.class)
    public DefaultClientResources coskyClientResources() {
        return DefaultClientResources.create();
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisConnectionFactory coskyRedisConnectionFactory(ClientResources clientResources, CoskyProperties coskyProperties) {
        return new RedisConnectionFactory(clientResources, coskyProperties.getRedis());
    }

//    @Bean(destroyMethod = "shutdown")
//    @ConditionalOnMissingBean
//    public AbstractRedisClient redisClient(ClientResources clientResources, CoskyProperties governProperties) {
//        return RedisClientSupport.redisClient(clientResources, governProperties.getRedis());
//    }

    @Bean
    @ConditionalOnMissingBean
    public RedisScriptInitializer redisScriptInitializer(RedisConnectionFactory redisConnectionFactory) {
        return new RedisScriptInitializer(redisConnectionFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageListenable messageListenable(RedisConnectionFactory redisConnectionFactory) {
        return redisConnectionFactory.getMessageListenable();
    }

    @Bean
    @ConditionalOnMissingBean
    public NamespaceService namespaceService(RedisConnectionFactory redisConnectionFactory) {
        return new RedisNamespaceService(redisConnectionFactory.getShareAsyncCommands());
    }
}
