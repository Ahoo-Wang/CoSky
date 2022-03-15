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

import me.ahoo.cosky.core.test.AbstractReactiveRedisTest;
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author ahoo wang
 */
public class RedisServiceRegistryTest extends AbstractReactiveRedisTest {
    private final static String namespace = "test_svc_csy";
    private ServiceInstance testInstance;
    private ServiceInstance testFixedInstance;
    private RedisServiceRegistry redisServiceRegistry;
    
    @BeforeEach
    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        testInstance = TestServiceInstance.randomInstance();
        testFixedInstance = TestServiceInstance.randomFixedInstance();
        RegistryProperties registryProperties = new RegistryProperties();
        registryProperties.setInstanceTtl(10);
        
        redisServiceRegistry = new RedisServiceRegistry(registryProperties, redisTemplate);
    }
    
    @AfterEach
    @Override
    public void destroy() {
        super.destroy();
    }
    
    @Test
    public void register() {
        Boolean result = redisServiceRegistry.register(namespace, testInstance).block();
        Assertions.assertEquals(Boolean.TRUE, result);
    }
    
    @Test
    public void renew() {
        Boolean result = redisServiceRegistry.renew(namespace, testInstance).block();
        Assertions.assertEquals(Boolean.TRUE, result);
    }
    
    @Test
    public void renewFixed() {
        Boolean result = redisServiceRegistry.renew(namespace, testFixedInstance).block();
        Assertions.assertEquals(Boolean.FALSE, result);
    }
    
    @Test
    public void registerFixed() {
        Boolean result = redisServiceRegistry.register(namespace, testFixedInstance).block();
        Assertions.assertEquals(Boolean.TRUE, result);
    }
    
    @Test
    public void deregister() {
        redisServiceRegistry.deregister(namespace, testInstance).block();
    }

    @Test
    public void registerRepeatedSync() {
        for (int i = 0; i < 20; i++) {
            redisServiceRegistry.register(namespace, testInstance).block();
        }
    }
}
