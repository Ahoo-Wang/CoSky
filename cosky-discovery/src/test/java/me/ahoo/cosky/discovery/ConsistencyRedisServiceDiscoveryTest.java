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

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.var;

import me.ahoo.cosky.core.test.AbstractReactiveRedisTest;
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery;
import me.ahoo.cosky.discovery.redis.RedisServiceDiscovery;
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author ahoo wang
 */
//TODO
@Disabled
@Slf4j
public class ConsistencyRedisServiceDiscoveryTest  extends AbstractReactiveRedisTest {

    private final static String namespace = "test_svc_csy";
    private ConsistencyRedisServiceDiscovery consistencyRedisServiceDiscovery;

    private ServiceInstance testInstance;
    private ServiceInstance testFixedInstance;
    private RedisServiceRegistry redisServiceRegistry;
    private RegistryProperties registryProperties;
    
    @BeforeEach
    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        testInstance = TestServiceInstance.randomInstance();
        testFixedInstance = TestServiceInstance.randomFixedInstance();
        registryProperties = new RegistryProperties();
        registryProperties.setInstanceTtl(30);
        redisServiceRegistry = new RedisServiceRegistry(registryProperties, redisTemplate);
        RedisServiceDiscovery redisServiceDiscovery = new RedisServiceDiscovery(redisTemplate);
        consistencyRedisServiceDiscovery = new ConsistencyRedisServiceDiscovery(redisServiceDiscovery, redisTemplate, listenerContainer);
    }
    @AfterEach
    @Override
    public void destroy() {
        super.destroy();
    }
    @Test
    public void getServices() {
        TestServiceInstance.registerRandomInstance(namespace, redisServiceRegistry, (instance -> {
            List<String> serviceIds = consistencyRedisServiceDiscovery.getServices(namespace).collectList().block();
            Assertions.assertNotNull(serviceIds);
            Assertions.assertTrue(serviceIds.contains(instance.getServiceId()));
        }));
    }

    @Test
    public void getInstances() {
        TestServiceInstance.registerRandomInstance(namespace, redisServiceRegistry, (instance -> {
            List<ServiceInstance> instances = consistencyRedisServiceDiscovery.getInstances(namespace, instance.getServiceId()).collectList().block();
            Assertions.assertNotNull(instances);
    
            ServiceInstance expectedInstance = instances.stream().findFirst().get();
            Assertions.assertNotNull(expectedInstance);
            Assertions.assertEquals(instance.getServiceId(), expectedInstance.getServiceId());
            Assertions.assertEquals(instance.getInstanceId(), expectedInstance.getInstanceId());
        }));
    }

    @Test
    public void getInstance() {
        TestServiceInstance.registerRandomInstance(namespace, redisServiceRegistry, (instance -> {
            ServiceInstance actualInstance = consistencyRedisServiceDiscovery.getInstance(namespace, instance.getServiceId(), instance.getInstanceId()).block();
            Assertions.assertEquals(instance.getServiceId(), actualInstance.getServiceId());
            Assertions.assertEquals(instance.getInstanceId(), actualInstance.getInstanceId());
        }));
    }

    @Test
    public void getInstanceWithCache() {
        TestServiceInstance.registerRandomInstance(namespace, redisServiceRegistry, (instance -> {
            consistencyRedisServiceDiscovery.getInstances(namespace, instance.getServiceId()).collectList().block();
            ServiceInstance actualInstance = consistencyRedisServiceDiscovery.getInstance(namespace, instance.getServiceId(), instance.getInstanceId()).block();
            Assertions.assertEquals(instance.getServiceId(), actualInstance.getServiceId());
            Assertions.assertEquals(instance.getInstanceId(), actualInstance.getInstanceId());
    
            ServiceInstance cachedInstance = consistencyRedisServiceDiscovery.getInstance(namespace, instance.getServiceId(), instance.getInstanceId()).block();
            Assertions.assertEquals(cachedInstance, actualInstance);
        }));
    }

    private final static int SLEEP_FOR_WAIT_MESSAGE = 1;

    @SneakyThrows
    protected void sleepForWaitNotify() {
        TimeUnit.SECONDS.sleep(SLEEP_FOR_WAIT_MESSAGE);
    }


    @Test
    public void getServicesListener() {
        List<String> services = consistencyRedisServiceDiscovery.getServices(namespace).collectList().block();
        sleepForWaitNotify();
        redisServiceRegistry.register(namespace, testInstance).block();
        sleepForWaitNotify();
        services = consistencyRedisServiceDiscovery.getServices(namespace).collectList().block();
        Assertions.assertEquals(1, services.size());
        redisServiceRegistry.register(namespace, testFixedInstance).block();
        sleepForWaitNotify();
        services = consistencyRedisServiceDiscovery.getServices(namespace).collectList().block();
        Assertions.assertEquals(2, services.size());
    }


    @Test
    public void getInstancesListener() {
        List<ServiceInstance> instances = consistencyRedisServiceDiscovery.getInstances(namespace, testInstance.getServiceId()).collectList().block();
        sleepForWaitNotify();
        redisServiceRegistry.register(namespace, testInstance).block();
        sleepForWaitNotify();
        instances = consistencyRedisServiceDiscovery.getInstances(namespace, testInstance.getServiceId()).collectList().block();
        Assertions.assertEquals(1, instances.size());
        redisServiceRegistry.deregister(namespace, testInstance).block();
        sleepForWaitNotify();
        instances = consistencyRedisServiceDiscovery.getInstances(namespace, testInstance.getServiceId()).collectList().block();
        Assertions.assertEquals(0, instances.size());
    }

    @SneakyThrows
    @Test
    public void getInstancesListenerExpire() {
        List<ServiceInstance> instances = consistencyRedisServiceDiscovery.getInstances(namespace, testInstance.getServiceId()).collectList().block();
        sleepForWaitNotify();
        redisServiceRegistry.register(namespace, testInstance).block();
        sleepForWaitNotify();
        instances = consistencyRedisServiceDiscovery.getInstances(namespace, testInstance.getServiceId()).collectList().block();
        Assertions.assertEquals(1, instances.size());
        TimeUnit.SECONDS.sleep(registryProperties.getInstanceTtl());
        instances = consistencyRedisServiceDiscovery.getInstances(namespace, testInstance.getServiceId()).collectList().block();
        Assertions.assertEquals(0, instances.size());
    }


    private final static int REPEATED_SIZE = 60000;
    private final static int THREAD_COUNT = 5;

    //    @Test
    public void getInstancesRepeated() {
        for (int i = 0; i < REPEATED_SIZE; i++) {
            getInstances();
        }
    }


    //    @Test
    public void getServicesRepeatedAsync() {
        var futures = new Flux[REPEATED_SIZE];
        for (int i = 0; i < REPEATED_SIZE; i++) {
            futures[i] = consistencyRedisServiceDiscovery.getServices(namespace);
        }
        Mono.when(futures).block();
    }

    //    @Test
    public void getInstancesRepeatedMMultiple() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(THREAD_COUNT);
        for (int thNum = 0; thNum < THREAD_COUNT; thNum++) {
            new Thread(() -> {
                getInstancesRepeated();
                countDownLatch.countDown();
            }).start();
        }
        countDownLatch.await();
    }

}
