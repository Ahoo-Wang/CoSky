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
import me.ahoo.cosky.discovery.redis.RedisServiceStatistic;
import me.ahoo.cosky.test.AbstractReactiveRedisTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * @author ahoo wang
 */
public class RedisServiceStatisticTest extends AbstractReactiveRedisTest {
    private final static String namespace = MockIdGenerator.INSTANCE.generateAsString();
    private RedisServiceStatistic redisServiceStatistic;
    
    private RedisServiceRegistry serviceRegistry;
    
    @BeforeEach
    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        RegistryProperties registryProperties = new RegistryProperties();
        serviceRegistry = new RedisServiceRegistry(registryProperties, redisTemplate);
        redisServiceStatistic = new RedisServiceStatistic(redisTemplate, listenerContainer);
    }
    
    @AfterEach
    @Override
    public void destroy() {
        super.destroy();
    }
    
    @Test
    void statService() {
        ServiceInstance getServiceStatInstance = TestServiceInstance.randomInstance();
        StepVerifier.create(serviceRegistry.register(namespace, getServiceStatInstance))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
        
        StepVerifier.create(redisServiceStatistic.statService(namespace))
            .verifyComplete();
        
        StepVerifier.create(redisServiceStatistic.getServiceStats(namespace).collectList())
            .expectNextMatches(serviceStats -> {
                Assertions.assertEquals(1, serviceStats.size());
                ServiceStat stat = serviceStats.get(0);
                Assertions.assertEquals(1, stat.getInstanceCount());
                return true;
            });
    }
}
