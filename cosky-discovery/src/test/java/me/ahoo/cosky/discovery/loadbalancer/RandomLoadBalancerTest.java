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

package me.ahoo.cosky.discovery.loadbalancer;

import me.ahoo.cosid.util.MockIdGenerator;
import me.ahoo.cosky.core.test.AbstractReactiveRedisTest;
import me.ahoo.cosky.discovery.RegistryProperties;
import me.ahoo.cosky.discovery.ServiceInstance;
import me.ahoo.cosky.discovery.TestServiceInstance;
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery;
import me.ahoo.cosky.discovery.redis.RedisServiceDiscovery;
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry;

import lombok.var;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * @author ahoo wang
 */
class RandomLoadBalancerTest extends AbstractReactiveRedisTest {
    private final static String namespace = "test_lb";
    private RedisServiceDiscovery redisServiceDiscovery;
    private RedisServiceRegistry redisServiceRegistry;
    private RandomLoadBalancer randomLoadBalancer;
    
    @BeforeEach
    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        RegistryProperties registryProperties = new RegistryProperties();
        redisServiceRegistry = new RedisServiceRegistry(registryProperties, redisTemplate);
        redisServiceDiscovery = new RedisServiceDiscovery(redisTemplate);
        ConsistencyRedisServiceDiscovery consistencyRedisServiceDiscovery =
            new ConsistencyRedisServiceDiscovery(redisServiceDiscovery, redisTemplate, listenerContainer);
        randomLoadBalancer = new RandomLoadBalancer(consistencyRedisServiceDiscovery);
    }
    
    @AfterEach
    @Override
    public void destroy() {
        super.destroy();
    }
    
    @Test
    void chooseNone() {
        ServiceInstance instance = randomLoadBalancer.choose(namespace, UUID.randomUUID().toString()).block();
        Assertions.assertNull(instance);
    }
    
    @Test
    void chooseOne() {
        TestServiceInstance.registerRandomInstance(namespace, redisServiceRegistry, instance -> {
            ServiceInstance expectedInstance = randomLoadBalancer.choose(namespace, instance.getServiceId()).block();
            Assertions.assertEquals(instance.getServiceId(), expectedInstance.getServiceId());
            Assertions.assertEquals(instance.getInstanceId(), expectedInstance.getInstanceId());
        });
    }
    
    @Test
    void chooseMultiple() {
        String serviceId = MockIdGenerator.INSTANCE.generateAsString();
        ServiceInstance instance1 = TestServiceInstance.createInstance(serviceId);
        ServiceInstance instance2 = TestServiceInstance.createInstance(serviceId);
        ServiceInstance instance3 = TestServiceInstance.createInstance(serviceId);
        redisServiceRegistry.register(namespace, instance1).block();
        redisServiceRegistry.register(namespace, instance2).block();
        redisServiceRegistry.register(namespace, instance3).block();
        ServiceInstance expectedInstance = randomLoadBalancer.choose(namespace, serviceId).block();
        Assertions.assertNotNull(expectedInstance);
        boolean succeeded = expectedInstance.getInstanceId().equals(instance1.getInstanceId())
            || expectedInstance.getInstanceId().equals(instance2.getInstanceId())
            || expectedInstance.getInstanceId().equals(instance3.getInstanceId());
        Assertions.assertTrue(succeeded);
        redisServiceRegistry.deregister(namespace, instance1).block();
        redisServiceRegistry.deregister(namespace, instance2).block();
        redisServiceRegistry.deregister(namespace, instance3).block();
    }
}
