/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

package me.ahoo.cosky.spring.cloud;

import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import me.ahoo.cosky.core.NamespaceService;
import me.ahoo.cosky.core.NamespacedContext;
import me.ahoo.cosky.core.listener.MessageListenable;
import me.ahoo.cosky.core.redis.RedisConnectionFactory;
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
