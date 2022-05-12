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
import me.ahoo.cosky.config.ConfigRollback;
import me.ahoo.cosky.config.ConfigService;
import me.ahoo.cosky.config.ConfigVersion;
import me.ahoo.cosky.test.AbstractReactiveRedisTest;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.test.StepVerifier;

/**
 * @author ahoo wang
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RedisConfigServiceTest extends AbstractReactiveRedisTest {
    private ConfigService configService;
    
    @BeforeAll
    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        configService = new RedisConfigService(redisTemplate);
    }
    
    @AfterAll
    @Override
    public void destroy() {
        super.destroy();
    }
    
    @Test
    public void setConfig() {
        
        final String namespace = MockIdGenerator.INSTANCE.generateAsString();
        final String testConfigId = MockIdGenerator.INSTANCE.generateAsString();
        StepVerifier
            .create(configService.setConfig(namespace, testConfigId, "setConfigData"))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
    }
    
    @Test
    public void removeConfig() {
        
        final String namespace = MockIdGenerator.INSTANCE.generateAsString();
        final String testConfigId = MockIdGenerator.INSTANCE.generateAsString();
        StepVerifier.create(
                configService.setConfig(namespace, testConfigId, "removeConfigData")
                    .then(configService.removeConfig(namespace, testConfigId))
            )
            .expectNext(Boolean.TRUE)
            .verifyComplete();
    }
    
    @Test
    public void getConfig() {
        
        final String namespace = MockIdGenerator.INSTANCE.generateAsString();
        final String testConfigId = MockIdGenerator.INSTANCE.generateAsString();
        String getConfigData = "getConfigData";
        StepVerifier.create(configService.setConfig(namespace, testConfigId, getConfigData))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
        
        StepVerifier.create(configService.getConfig(namespace, testConfigId))
            .expectNextMatches(actual -> {
                Assertions.assertEquals(testConfigId, actual.getConfigId());
                Assertions.assertEquals(getConfigData, actual.getData());
                Assertions.assertEquals(1, actual.getVersion());
                return true;
            })
            .verifyComplete();
    }
    
    @Test
    public void rollback() {
        
        final String namespace = MockIdGenerator.INSTANCE.generateAsString();
        final String testConfigId = MockIdGenerator.INSTANCE.generateAsString();
        String version1Data = "version-1";
        
        StepVerifier.create(configService.setConfig(namespace, testConfigId, version1Data))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
        
        StepVerifier.create(configService.getConfig(namespace, testConfigId))
            .expectNextMatches(config -> {
                Assertions.assertEquals(version1Data, config.getData());
                return true;
            })
            .verifyComplete();
        
        String version2Data = "version-2";
        StepVerifier.create(configService.setConfig(namespace, testConfigId, version2Data))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
        
        StepVerifier.create(configService.getConfig(namespace, testConfigId))
            .expectNextMatches(config -> {
                Assertions.assertEquals(version2Data, config.getData());
                return true;
            })
            .verifyComplete();
        
        StepVerifier.create(configService.rollback(namespace, testConfigId, 1))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
        
        StepVerifier.create(configService.getConfig(namespace, testConfigId))
            .expectNextMatches(config -> {
                Assertions.assertEquals(version1Data, config.getData());
                return true;
            })
            .verifyComplete();
    }
    
    @Test
    void getConfigs() {
        
        final String namespace = MockIdGenerator.INSTANCE.generateAsString();
        final String testConfigId = MockIdGenerator.INSTANCE.generateAsString();
        StepVerifier.create(configService.getConfigs(namespace))
            .expectNextCount(0)
            .verifyComplete();
        
        StepVerifier.create(configService.setConfig(namespace, testConfigId, "getConfigsData"))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
        
        StepVerifier.create(configService.getConfigs(namespace))
            .expectNext(testConfigId)
            .verifyComplete();
    }
    
    @Test
    void getConfigVersions() {
        
        final String namespace = MockIdGenerator.INSTANCE.generateAsString();
        final String testConfigId = MockIdGenerator.INSTANCE.generateAsString();
        getConfigVersions(namespace, testConfigId);
    }
    
    private void getConfigVersions(String namespace, String testConfigId) {
        StepVerifier.create(configService.setConfig(namespace, testConfigId, "getConfigVersionData"))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
        
        StepVerifier.create(configService.getConfigVersions(namespace, testConfigId))
            .expectNextCount(0)
            .verifyComplete();
        
        StepVerifier.create(configService.setConfig(namespace, testConfigId, "getConfigVersionData-1"))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
        
        StepVerifier.create(configService.getConfigVersions(namespace, testConfigId))
            .expectNextMatches(configVersion -> {
                Assertions.assertEquals(testConfigId, configVersion.getConfigId());
                Assertions.assertEquals(1, configVersion.getVersion());
                return true;
            })
            .verifyComplete();
    }
    
    @Test
    void getConfigVersionsLast10() {
        
        final String namespace = MockIdGenerator.INSTANCE.generateAsString();
        final String testConfigId = MockIdGenerator.INSTANCE.generateAsString();
        for (int i = 0; i < ConfigRollback.HISTORY_SIZE * 2 + 1; i++) {
            StepVerifier.create(configService.setConfig(namespace, testConfigId, "getConfigVersionData-" + i))
                .expectNext(Boolean.TRUE)
                .verifyComplete();
        }
        StepVerifier.create(configService.getConfigVersions(namespace, testConfigId).collectList())
            .expectNextMatches(configVersions -> {
                Assertions.assertEquals(ConfigRollback.HISTORY_SIZE, configVersions.size());
                ConfigVersion configVersion = configVersions.get(0);
                Assertions.assertEquals(testConfigId, configVersion.getConfigId());
                Assertions.assertEquals(ConfigRollback.HISTORY_SIZE * 2, configVersion.getVersion());
                return true;
            })
            .verifyComplete();
    }
    
    @Test
    void getConfigHistory() {
        
        final String namespace = MockIdGenerator.INSTANCE.generateAsString();
        final String testConfigId = MockIdGenerator.INSTANCE.generateAsString();
        getConfigVersions(namespace, testConfigId);
        StepVerifier.create(configService.getConfigHistory(namespace, testConfigId, 1))
            .expectNextMatches(configHistory -> {
                Assertions.assertEquals(testConfigId, configHistory.getConfigId());
                Assertions.assertEquals(1, configHistory.getVersion());
                return true;
            })
            .verifyComplete();
    }
}
