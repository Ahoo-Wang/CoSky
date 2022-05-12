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

package me.ahoo.cosky.test;

import io.lettuce.core.resource.ClientResources;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import reactor.core.publisher.Hooks;

/**
 * ReactiveRedisTest .
 *
 * @author ahoo wang
 */
public abstract class AbstractReactiveRedisTest implements InitializingBean, DisposableBean {
    public static final ClientResources SHARE = ClientResources.builder().build();
    protected LettuceConnectionFactory connectionFactory;
    protected ReactiveStringRedisTemplate redisTemplate;
    protected ReactiveRedisMessageListenerContainer listenerContainer;
    
    @Override
    public void afterPropertiesSet() {
        if (enableOperatorDebug()) {
            Hooks.onOperatorDebug();
        }
        
        ClientResources clientResources = enableShare() ? SHARE : ClientResources.builder().build();
        
        LettuceClientConfiguration lettuceClientConfiguration = LettuceClientConfiguration
            .builder()
            .clientResources(clientResources)
            .build();
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        connectionFactory = new LettuceConnectionFactory(redisConfig, lettuceClientConfiguration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.setShareNativeConnection(enableShare());
        customizeConnectionFactory(connectionFactory);
        redisTemplate = new ReactiveStringRedisTemplate(connectionFactory);
        listenerContainer = new ReactiveRedisMessageListenerContainer(connectionFactory);
    }
    
    protected void customizeConnectionFactory(LettuceConnectionFactory connectionFactory) {
    
    }
    
    protected boolean enableOperatorDebug() {
        return false;
    }
    
    protected boolean enableShare() {
        return false;
    }
    
    @Override
    public void destroy() {
        if (null != listenerContainer) {
            listenerContainer.destroy();
        }
        if (null != connectionFactory) {
            connectionFactory.destroy();
        }
    }
}
