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

import me.ahoo.cosid.util.MockIdGenerator;
import me.ahoo.cosky.config.Config;
import me.ahoo.cosky.core.test.AbstractReactiveRedisTest;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.test.StepVerifier;

import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * @author ahoo wang
 */
@Slf4j
class ConsistencyRedisConfigServiceTest extends AbstractReactiveRedisTest {
    
    private ConsistencyRedisConfigService consistencyRedisConfigService;
    
    private final String namespace = "csy__" + MockIdGenerator.INSTANCE.generateAsString();
    
    @BeforeEach
    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        RedisConfigService redisConfigService = new RedisConfigService(redisTemplate);
        consistencyRedisConfigService = new ConsistencyRedisConfigService(redisConfigService, listenerContainer);
    }
    
    @AfterEach
    @Override
    public void destroy() {
        super.destroy();
    }
    
    @Test
    void getConfig() {
        String testConfigId = MockIdGenerator.INSTANCE.generateAsString();
        String getConfigData = "getConfigData";
        StepVerifier.create(consistencyRedisConfigService.setConfig(namespace, testConfigId, getConfigData))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
        
        StepVerifier.create(consistencyRedisConfigService.getConfig(namespace, testConfigId))
            .expectNextMatches(config -> {
                Assertions.assertEquals(testConfigId, config.getConfigId());
                Assertions.assertEquals(getConfigData, config.getData());
                Assertions.assertEquals(1, config.getVersion());
                return true;
            })
            .verifyComplete();
        
        StepVerifier.create(consistencyRedisConfigService.getConfig(namespace, testConfigId).zipWith(consistencyRedisConfigService.getConfig(namespace, testConfigId)))
            // here is "==" .
            .expectNextMatches(config2 -> config2.getT1() == config2.getT2())
            .verifyComplete();
    }
    
    //TODO
    @Disabled
    @SneakyThrows
    @Test
    void getConfigChanged() {
        String testConfigId = MockIdGenerator.INSTANCE.generateAsString();
        Semaphore semaphore = new Semaphore(0);
        Disposable disposable = consistencyRedisConfigService.listen(namespace, testConfigId)
            .doOnNext(configEvent -> Executors.newSingleThreadScheduledExecutor().schedule(() -> semaphore.release(), 10, TimeUnit.MILLISECONDS))
            .subscribe();
        Config getActual = consistencyRedisConfigService.getConfig(namespace, testConfigId).block();
        Assertions.assertNull(getActual);
        
        String getConfigData = "getConfigData";
        Boolean setActual = consistencyRedisConfigService.setConfig(namespace, testConfigId, getConfigData).block();
        Assertions.assertEquals(Boolean.TRUE, setActual);
        Assertions.assertTrue(semaphore.tryAcquire(1, TimeUnit.SECONDS));
        
        Config getActual1 = consistencyRedisConfigService.getConfig(namespace, testConfigId).block();
        Assertions.assertNotNull(getActual1);
        String getConfigData2 = "getConfigData-2";
        setActual = consistencyRedisConfigService.setConfig(namespace, testConfigId, getConfigData2).block();
        Assertions.assertEquals(Boolean.TRUE, setActual);
        Assertions.assertTrue(semaphore.tryAcquire(1, TimeUnit.SECONDS));
        Config getActual2 = consistencyRedisConfigService.getConfig(namespace, testConfigId).block();
        Assertions.assertEquals(getConfigData2, getActual2.getData());
        Assertions.assertNotEquals(getActual1, getActual2);
        disposable.dispose();
    }
    
    //TODO
    @Disabled
    @SneakyThrows
    @Test
    void getConfigChangedRemove() {
        String testConfigId = MockIdGenerator.INSTANCE.generateAsString();
        Semaphore semaphore = new Semaphore(0);
        
        Disposable disposable = consistencyRedisConfigService.listen(namespace, testConfigId)
            .doOnNext(configEvent -> {
                Assertions.assertEquals(configEvent.getNamespacedConfigId().getConfigId(), testConfigId);
                Executors.newSingleThreadScheduledExecutor().schedule(() -> semaphore.release(), 10, TimeUnit.MILLISECONDS);
            })
            .subscribe();
        
        Config getActual = consistencyRedisConfigService.getConfig(namespace, testConfigId).block();
        Assertions.assertNull(getActual);
        String getConfigData = "getConfigChangedRemoveData";
        Boolean setActual = consistencyRedisConfigService.setConfig(namespace, testConfigId, getConfigData).block();
        Assertions.assertEquals(Boolean.TRUE, setActual);
        Assertions.assertTrue(semaphore.tryAcquire(1, TimeUnit.SECONDS));
        getActual = consistencyRedisConfigService.getConfig(namespace, testConfigId).block();
        Assertions.assertNotNull(getActual);
        Boolean removeActual = consistencyRedisConfigService.removeConfig(namespace, testConfigId).block();
        Assertions.assertEquals(Boolean.TRUE, removeActual);
        Assertions.assertTrue(semaphore.tryAcquire(1, TimeUnit.SECONDS));
        Config getActual2 = consistencyRedisConfigService.getConfig(namespace, testConfigId).block();
        Assertions.assertNull(getActual2);
        disposable.dispose();
    }
    
    //TODO
    @Disabled
    @SneakyThrows
    @Test
    void getConfigChangedRollback() {
        String testConfigId = MockIdGenerator.INSTANCE.generateAsString();
        Semaphore semaphore = new Semaphore(0);
        Disposable disposable = consistencyRedisConfigService.listen(namespace, testConfigId)
            .doOnNext(configEvent -> Executors.newSingleThreadScheduledExecutor().schedule(() -> semaphore.release(), 10, TimeUnit.MILLISECONDS))
            .subscribe();
        
        String version1Data = "version-1";
        Boolean setActual = consistencyRedisConfigService.setConfig(namespace, testConfigId, version1Data).block();
        Assertions.assertEquals(Boolean.TRUE, setActual);
        Config version1Config = consistencyRedisConfigService.getConfig(namespace, testConfigId).block();
        Assertions.assertNotNull(version1Config);
        Assertions.assertEquals(version1Data, version1Config.getData());
        
        String version2Data = "version-2";
        setActual = consistencyRedisConfigService.setConfig(namespace, testConfigId, version2Data).block();
        Assertions.assertEquals(Boolean.TRUE, setActual);
        Assertions.assertTrue(semaphore.tryAcquire(1, TimeUnit.SECONDS));
        Config version2Config = consistencyRedisConfigService.getConfig(namespace, testConfigId).block();
        Assertions.assertNotNull(version2Config);
        Assertions.assertEquals(version2Data, version2Config.getData());
        
        Boolean rollbackActual = consistencyRedisConfigService.rollback(namespace, testConfigId, version1Config.getVersion()).block();
        Assertions.assertEquals(Boolean.TRUE, rollbackActual);
        Assertions.assertTrue(semaphore.tryAcquire(1, TimeUnit.SECONDS));
        Config afterRollbackConfig = consistencyRedisConfigService.getConfig(namespace, testConfigId).block();
        Assertions.assertNotNull(afterRollbackConfig);
        Assertions.assertEquals(version1Config.getData(), afterRollbackConfig.getData());
        disposable.dispose();
    }
}
