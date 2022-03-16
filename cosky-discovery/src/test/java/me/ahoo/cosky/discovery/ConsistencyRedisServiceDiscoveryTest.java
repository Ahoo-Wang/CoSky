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

import me.ahoo.cosid.util.MockIdGenerator;
import me.ahoo.cosky.core.test.AbstractReactiveRedisTest;
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery;
import me.ahoo.cosky.discovery.redis.RedisServiceDiscovery;
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * @author ahoo wang
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ConsistencyRedisServiceDiscoveryTest extends AbstractReactiveRedisTest {
    private RedisServiceDiscovery redisServiceDiscovery;
    private ConsistencyRedisServiceDiscovery serviceDiscovery;
    private RedisServiceRegistry redisServiceRegistry;
    private RegistryProperties registryProperties;
    
    @BeforeAll
    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        registryProperties = new RegistryProperties();
        registryProperties.setInstanceTtl(Duration.ofSeconds(5));
        redisServiceRegistry = new RedisServiceRegistry(registryProperties, redisTemplate);
        redisServiceDiscovery = new RedisServiceDiscovery(redisTemplate);
        serviceDiscovery = new ConsistencyRedisServiceDiscovery(redisServiceDiscovery, redisTemplate, listenerContainer);
    }
    
    @AfterAll
    @Override
    public void destroy() {
        super.destroy();
    }
    
    @Test
    public void getServices() {
        final String namespace = MockIdGenerator.INSTANCE.generateAsString();
        TestServiceInstance.registerRandomInstanceAndTestThenDeregister(namespace, redisServiceRegistry, (instance -> {
            StepVerifier.create(serviceDiscovery.getServices(namespace).collectList())
                .expectNextMatches(serviceIds -> serviceIds.contains(instance.getServiceId()))
                .verifyComplete();
        }));
    }
    
    @Test
    public void getInstances() {
        final String namespace = MockIdGenerator.INSTANCE.generateAsString();
        TestServiceInstance.registerRandomInstanceAndTestThenDeregister(namespace, redisServiceRegistry, (instance -> {
            StepVerifier.create(serviceDiscovery.getInstances(namespace, instance.getServiceId()).collectList())
                .expectNextMatches(serviceInstances -> serviceInstances.contains(instance))
                .verifyComplete();
        }));
    }
    
    @Test
    public void getInstance() {
        final String namespace = MockIdGenerator.INSTANCE.generateAsString();
        TestServiceInstance.registerRandomInstanceAndTestThenDeregister(namespace, redisServiceRegistry, (instance -> {
            StepVerifier.create(serviceDiscovery.getInstance(namespace, instance.getServiceId(), instance.getInstanceId()))
                .expectNext(instance)
                .verifyComplete();
        }));
    }
    
    @Test
    public void getInstanceWithCache() {
        final String namespace = MockIdGenerator.INSTANCE.generateAsString();
        TestServiceInstance.registerRandomInstanceAndTestThenDeregister(namespace, redisServiceRegistry, (instance -> {
            StepVerifier.create(serviceDiscovery.getInstances(namespace, instance.getServiceId()).collectList())
                .expectNextMatches(serviceInstances -> serviceInstances.contains(instance))
                .verifyComplete();
            
            StepVerifier.create(serviceDiscovery.getInstance(namespace, instance.getServiceId(), instance.getInstanceId())
                    .zipWith(serviceDiscovery.getInstance(namespace, instance.getServiceId(), instance.getInstanceId())))
                .expectNextMatches(tuple -> tuple.getT1() == tuple.getT2())
                .verifyComplete();
        }));
    }
    
    @SneakyThrows
    @Test
    public void getServicesCache() {
        final String namespace = MockIdGenerator.INSTANCE.generateAsString();
        final String serviceId = MockIdGenerator.INSTANCE.generateAsString();
        Semaphore semaphore = new Semaphore(0);
        ServiceDiscovery serviceDiscovery = new ConsistencyRedisServiceDiscovery(redisServiceDiscovery, redisTemplate, listenerContainer,
            (serviceChangedEvent -> {
            }),
            (ns -> semaphore.release())
        );
        
        StepVerifier.create(serviceDiscovery.getServices(namespace))
            .expectNextCount(0)
            .verifyComplete();
        
        StepVerifier.create(redisServiceRegistry.register(namespace, TestServiceInstance.createInstance(serviceId)))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
        Assertions.assertTrue(semaphore.tryAcquire(1, TimeUnit.SECONDS));
        
        StepVerifier.create(serviceDiscovery.getServices(namespace).collectList())
            .expectNextMatches(services -> services.contains(serviceId))
            .verifyComplete();
        
        final String serviceId2 = MockIdGenerator.INSTANCE.generateAsString();
        
        StepVerifier.create(redisServiceRegistry.register(namespace, TestServiceInstance.createInstance(serviceId2)))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
        Assertions.assertTrue(semaphore.tryAcquire(1, TimeUnit.SECONDS));
        
        StepVerifier.create(serviceDiscovery.getServices(namespace).collectList())
            .expectNextMatches(services -> {
                Assertions.assertTrue(services.contains(serviceId));
                Assertions.assertTrue(services.contains(serviceId2));
                Assertions.assertEquals(2, services.size());
                return true;
            })
            .verifyComplete();
        
        
    }
    
    @SneakyThrows
    @Test
    public void getInstancesCache() {
        final String namespace = MockIdGenerator.INSTANCE.generateAsString();
        final String serviceId = MockIdGenerator.INSTANCE.generateAsString();
        final ServiceInstance instance = TestServiceInstance.createInstance(serviceId);
        Semaphore semaphore = new Semaphore(0);
        ServiceDiscovery serviceDiscovery = new ConsistencyRedisServiceDiscovery(redisServiceDiscovery, redisTemplate, listenerContainer,
            (serviceChangedEvent -> {
                semaphore.release();
            }),
            (ns -> {
            })
        );
        StepVerifier.create(serviceDiscovery.getInstances(namespace, serviceId))
            .expectNextCount(0)
            .verifyComplete();
        
        StepVerifier.create(redisServiceRegistry.register(namespace, instance))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
        Assertions.assertTrue(semaphore.tryAcquire(1, TimeUnit.SECONDS));
        
        StepVerifier.create(serviceDiscovery.getInstances(namespace, serviceId).collectList())
            .expectNextMatches(serviceInstances -> serviceInstances.contains(instance))
            .verifyComplete();
        
        StepVerifier.create(redisServiceRegistry.deregister(namespace, instance))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
        Assertions.assertTrue(semaphore.tryAcquire(1, TimeUnit.SECONDS));
        
        StepVerifier.create(serviceDiscovery.getInstances(namespace, serviceId))
            .expectNextCount(0)
            .verifyComplete();
    }
    
    @Test
    public void getInstancesListenerExpire() throws InterruptedException {
        final String namespace = MockIdGenerator.INSTANCE.generateAsString();
        final String serviceId = MockIdGenerator.INSTANCE.generateAsString();
        final ServiceInstance instance = TestServiceInstance.createInstance(serviceId);
        Semaphore semaphore = new Semaphore(0);
        ServiceDiscovery serviceDiscovery = new ConsistencyRedisServiceDiscovery(redisServiceDiscovery, redisTemplate, listenerContainer,
            (serviceChangedEvent -> {
                semaphore.release();
            }),
            (ns -> {
            })
        );
        StepVerifier.create(serviceDiscovery.getInstances(namespace, serviceId))
            .expectNextCount(0)
            .verifyComplete();
        
        StepVerifier.create(redisServiceRegistry.register(namespace, instance))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
        Assertions.assertTrue(semaphore.tryAcquire(1, TimeUnit.SECONDS));
        
        StepVerifier.create(serviceDiscovery.getInstances(namespace, serviceId).collectList())
            .expectNextMatches(serviceInstances -> serviceInstances.contains(instance))
            .verifyComplete();
        
        // wait for ttl
        TimeUnit.MILLISECONDS.sleep(registryProperties.getInstanceTtl().plusSeconds(1).toMillis());
        
        StepVerifier.create(serviceDiscovery.getInstances(namespace, serviceId).collectList())
            .expectNextMatches(serviceInstances -> serviceInstances.size() == 0)
            .verifyComplete();
    }
}
