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
import me.ahoo.cosky.discovery.redis.RedisServiceDiscovery;
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * @author ahoo wang
 */
@Slf4j
public class RedisServiceDiscoveryTest extends AbstractReactiveRedisTest {
    private final static String namespace = "test_svc";
    private RedisServiceDiscovery redisServiceDiscovery;
    private RedisServiceRegistry redisServiceRegistry;
    
    @BeforeEach
    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        RegistryProperties registryProperties = new RegistryProperties();
        redisServiceRegistry = new RedisServiceRegistry(registryProperties, redisTemplate);
        redisServiceDiscovery = new RedisServiceDiscovery(redisTemplate);
    }
    
    @AfterEach
    @Override
    public void destroy() {
        super.destroy();
    }

    @Test
    public void getServices() {
        TestServiceInstance.registerRandomInstanceAndTestThenDeregister(namespace, redisServiceRegistry, (instance -> {
            List<String> serviceIds = redisServiceDiscovery.getServices(namespace).collectList().block();
            Assertions.assertNotNull(serviceIds);
            Assertions.assertTrue(serviceIds.contains(instance.getServiceId()));
        }));
    }
    
    @Test
    public void getInstances() {
        TestServiceInstance.registerRandomInstanceAndTestThenDeregister(namespace, redisServiceRegistry, (instance -> {
            List<ServiceInstance> instances = redisServiceDiscovery.getInstances(namespace, instance.getServiceId()).collectList().block();
            Assertions.assertNotNull(instances);
            Assertions.assertEquals(1, instances.size());
    
            ServiceInstance expectedInstance = instances.stream().findFirst().get();
            Assertions.assertEquals(instance.getServiceId(), expectedInstance.getServiceId());
            Assertions.assertEquals(instance.getInstanceId(), expectedInstance.getInstanceId());
        }));
    }
    
    
    @Test
    public void getInstance() {
        TestServiceInstance.registerRandomInstanceAndTestThenDeregister(namespace, redisServiceRegistry, (instance -> {
            ServiceInstance actualInstance = redisServiceDiscovery.getInstance(namespace, instance.getServiceId(), instance.getInstanceId()).block();
            Assertions.assertEquals(instance.getServiceId(), actualInstance.getServiceId());
            Assertions.assertEquals(instance.getInstanceId(), actualInstance.getInstanceId());
        }));
    }
    
}
