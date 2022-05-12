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

package me.ahoo.cosky.config.redis;

import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosky.config.ConfigService;
import me.ahoo.cosky.core.test.AbstractReactiveRedisTest;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.test.StepVerifier;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * @author ahoo wang
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsistencyRedisConfigServiceTest extends AbstractReactiveRedisTest {
    
    private RedisConfigService redisConfigService;
    
    @BeforeAll
    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        redisConfigService = new RedisConfigService(redisTemplate);
    }
    
    @AfterAll
    @Override
    public void destroy() {
        super.destroy();
    }
    
    @Test
    void getConfig() {
        final String namespace = MockIdGenerator.INSTANCE.generateAsString();
        final String testConfigId = MockIdGenerator.INSTANCE.generateAsString();
        
        ConfigService configService = new ConsistencyRedisConfigService(redisConfigService, listenerContainer);
        String getConfigData = "getConfigData";
        StepVerifier.create(configService.setConfig(namespace, testConfigId, getConfigData))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
        
        StepVerifier.create(configService.getConfig(namespace, testConfigId))
            .expectNextMatches(config -> {
                Assertions.assertEquals(testConfigId, config.getConfigId());
                Assertions.assertEquals(getConfigData, config.getData());
                Assertions.assertEquals(1, config.getVersion());
                return true;
            })
            .verifyComplete();
        
        StepVerifier.create(configService.getConfig(namespace, testConfigId).zipWith(configService.getConfig(namespace, testConfigId)))
            // here is "==" .
            .expectNextMatches(config2 -> config2.getT1() == config2.getT2())
            .verifyComplete();
    }
    
    @SneakyThrows
    @Test
    void getConfigChanged() {
        final String namespace = MockIdGenerator.INSTANCE.generateAsString();
        final String testConfigId = MockIdGenerator.INSTANCE.generateAsString();
        
        Semaphore semaphore = new Semaphore(0);
        ConfigService configService = new ConsistencyRedisConfigService(redisConfigService, listenerContainer, (configChangedEvent -> {
            if (configChangedEvent.getNamespacedConfigId().getNamespace().equals(namespace)){
                semaphore.release();
            }
        }));
        
        StepVerifier.create(configService.getConfig(namespace, testConfigId))
            .expectNextCount(0)
            .verifyComplete();
        
        String configData = MockIdGenerator.INSTANCE.generateAsString();
        
        StepVerifier.create(configService.setConfig(namespace, testConfigId, configData))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
        Assertions.assertTrue(semaphore.tryAcquire(5, TimeUnit.SECONDS));
        
        StepVerifier.create(configService.getConfig(namespace, testConfigId))
            .expectNextMatches(config -> configData.equals(config.getData()))
            .verifyComplete();
        
        StepVerifier.create(configService.getConfig(namespace, testConfigId).zipWith(configService.getConfig(namespace, testConfigId)))
            // here is "==" .
            .expectNextMatches(config2 -> config2.getT1() == config2.getT2())
            .verifyComplete();
    }
    
    @SneakyThrows
    @Test
    void getConfigChangedRemove() {
        final String namespace = MockIdGenerator.INSTANCE.generateAsString();
        final String testConfigId = MockIdGenerator.INSTANCE.generateAsString();
        
        Semaphore semaphore = new Semaphore(0);
        ConfigService configService = new ConsistencyRedisConfigService(redisConfigService, listenerContainer, (configChangedEvent -> semaphore.release()));
        StepVerifier.create(configService.getConfig(namespace, testConfigId))
            .expectNextCount(0)
            .verifyComplete();
        
        String configData = "configData";
        
        StepVerifier.create(configService.setConfig(namespace, testConfigId, configData))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
        Assertions.assertTrue(semaphore.tryAcquire(5, TimeUnit.SECONDS));
        
        StepVerifier.create(configService.getConfig(namespace, testConfigId))
            .expectNextMatches(config -> configData.equals(config.getData()))
            .verifyComplete();
        
        StepVerifier.create(configService.removeConfig(namespace, testConfigId))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
        Assertions.assertTrue(semaphore.tryAcquire(5, TimeUnit.SECONDS));
        
        StepVerifier.create(configService.getConfig(namespace, testConfigId))
            .expectNextCount(0)
            .verifyComplete();
    }
    
    @SneakyThrows
    @Test
    void getConfigChangedRollback() {
        final String namespace = MockIdGenerator.INSTANCE.generateAsString();
        final String testConfigId = MockIdGenerator.INSTANCE.generateAsString();
        
        Semaphore semaphore = new Semaphore(0);
        ConfigService configService = new ConsistencyRedisConfigService(redisConfigService, listenerContainer, (configChangedEvent -> semaphore.release()));
        StepVerifier.create(configService.getConfig(namespace, testConfigId))
            .expectNextCount(0)
            .verifyComplete();
        
        String version1Data = "version-1";
        StepVerifier.create(configService.setConfig(namespace, testConfigId, version1Data))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
        Assertions.assertTrue(semaphore.tryAcquire(5, TimeUnit.SECONDS));
        
        StepVerifier.create(configService.getConfig(namespace, testConfigId))
            .expectNextMatches(config -> version1Data.equals(config.getData()))
            .verifyComplete();
        
        String version2Data = "version-2";
        
        StepVerifier.create(configService.setConfig(namespace, testConfigId, version2Data))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
        Assertions.assertTrue(semaphore.tryAcquire(5, TimeUnit.SECONDS));
        
        StepVerifier.create(configService.getConfig(namespace, testConfigId))
            .expectNextMatches(config -> version2Data.equals(config.getData()))
            .verifyComplete();
        
        StepVerifier.create(configService.rollback(namespace, testConfigId, 1))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
        Assertions.assertTrue(semaphore.tryAcquire(5, TimeUnit.SECONDS));
        
        StepVerifier.create(configService.getConfig(namespace, testConfigId))
            .expectNextMatches(config -> version1Data.equals(config.getData()))
            .verifyComplete();
    }
}
