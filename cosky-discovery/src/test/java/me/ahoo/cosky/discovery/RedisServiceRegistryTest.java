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

package me.ahoo.cosky.discovery;

import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry;
import me.ahoo.cosky.test.AbstractReactiveRedisTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;

/**
 * @author ahoo wang
 */
public class RedisServiceRegistryTest extends AbstractReactiveRedisTest {
    private final static String namespace = MockIdGenerator.INSTANCE.generateAsString();
    private RedisServiceRegistry serviceRegistry;
    
    @BeforeEach
    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        RegistryProperties registryProperties = new RegistryProperties();
        registryProperties.setInstanceTtl(Duration.ofSeconds(10));
        
        serviceRegistry = new RedisServiceRegistry(registryProperties, redisTemplate);
    }
    
    @AfterEach
    @Override
    public void destroy() {
        super.destroy();
    }
    
    @Test
    public void setService() {
        StepVerifier.create(serviceRegistry.setService(namespace, MockIdGenerator.INSTANCE.generateAsString()))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
    }
    
    @Test
    public void register() {
        StepVerifier.create(serviceRegistry.register(namespace, TestServiceInstance.randomInstance()))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
    }
    
    @Test
    public void renew() {
        StepVerifier.create(serviceRegistry.renew(namespace, TestServiceInstance.randomInstance()))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
    }
    
    @Test
    public void renewFixed() {
        StepVerifier.create(serviceRegistry.renew(namespace, TestServiceInstance.randomFixedInstance()))
            .expectNext(Boolean.FALSE)
            .verifyComplete();
    }
    
    @Test
    public void registerFixed() {
        StepVerifier.create(serviceRegistry.register(namespace, TestServiceInstance.randomFixedInstance()))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
    }
    
    @Test
    public void deregister() {
        ServiceInstance testInstance = TestServiceInstance.randomInstance();
        StepVerifier.create(serviceRegistry.deregister(namespace, testInstance))
            .expectNext(Boolean.FALSE)
            .verifyComplete();
        
        StepVerifier.create(serviceRegistry.register(namespace, testInstance))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
        
        StepVerifier.create(serviceRegistry.deregister(namespace, testInstance))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
    }
    
    @Test
    public void registerRepeatedSync() {
        ServiceInstance testInstance = TestServiceInstance.randomInstance();
        
        for (int i = 0; i < 20; i++) {
            StepVerifier.create(serviceRegistry.register(namespace, testInstance))
                .expectNext(Boolean.TRUE)
                .verifyComplete();
        }
    }
}
