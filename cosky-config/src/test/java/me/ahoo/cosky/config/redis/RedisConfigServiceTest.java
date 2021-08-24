/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

import lombok.var;
import me.ahoo.cosky.config.ConfigRollback;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * @author ahoo wang
 */
public class RedisConfigServiceTest extends BaseOnRedisClientTest {
    private RedisConfigService redisConfigService;
    private final String testConfigId = "test_config";
    private final String namespace = "test_cfg";

    @BeforeAll
    private void init() {
        redisConfigService = new RedisConfigService(redisConnection.reactive());
    }

    @Test
    public void setConfig() {
        clearTestData(namespace);
        var setResult = redisConfigService.setConfig(namespace, testConfigId, "setConfigData").block();
        Assertions.assertTrue(setResult);
    }


    @Test
    public void removeConfig() {
        clearTestData(namespace);
        redisConfigService.setConfig(namespace, testConfigId, "removeConfigData").block();
        var result = redisConfigService.removeConfig(namespace, testConfigId).block();
        Assertions.assertTrue(result);
    }

    @Test
    public void getConfig() {
        clearTestData(namespace);
        var getConfigData = "getConfigData";
        var setResult = redisConfigService.setConfig(namespace, testConfigId, getConfigData).block();
        Assertions.assertTrue(setResult);
        var getResult = redisConfigService.getConfig(namespace, testConfigId).block();
        Assertions.assertNotNull(getResult);
        Assertions.assertEquals(testConfigId, getResult.getConfigId());
        Assertions.assertEquals(getConfigData, getResult.getData());
        Assertions.assertEquals(1, getResult.getVersion());
    }


    @Test
    public void rollback() {
        clearTestData(namespace);
        var test_get_config = "test_rollback_config";
        var version1Data = "version-1";
        var setResult = redisConfigService.setConfig(namespace, test_get_config, version1Data).block();
        Assertions.assertTrue(setResult);
        var version1Config = redisConfigService.getConfig(namespace, test_get_config).block();
        Assertions.assertNotNull(version1Config);
        Assertions.assertEquals(version1Data, version1Config.getData());

        var version2Data = "version-2";
        setResult = redisConfigService.setConfig(namespace, test_get_config, version2Data).block();
        Assertions.assertTrue(setResult);
        var version2Config = redisConfigService.getConfig(namespace, test_get_config).block();
        Assertions.assertNotNull(version2Config);
        Assertions.assertEquals(version2Data, version2Config.getData());

        var rollbackResult = redisConfigService.rollback(namespace, test_get_config, version1Config.getVersion()).block();
        Assertions.assertTrue(rollbackResult);

        var afterRollbackConfig = redisConfigService.getConfig(namespace, test_get_config).block();

        Assertions.assertEquals(version1Config.getData(), afterRollbackConfig.getData());
    }

    @Test
    void getConfigs() {
        clearTestData(namespace);
        var getResult = redisConfigService.getConfigs(namespace).block();
        Assertions.assertTrue(getResult.isEmpty());
        var setResult = redisConfigService.setConfig(namespace, testConfigId, "getConfigsData").block();
        Assertions.assertTrue(setResult);
        getResult = redisConfigService.getConfigs(namespace).block();
        Assertions.assertFalse(getResult.isEmpty());
        Assertions.assertEquals(testConfigId, getResult.iterator().next());
    }

    @Test
    void getConfigVersions() {
        clearTestData(namespace);
        var setResult = redisConfigService.setConfig(namespace, testConfigId, "getConfigVersionData").block();
        Assertions.assertTrue(setResult);
        var getConfigVersionResult = redisConfigService.getConfigVersions(namespace, testConfigId).block();
        Assertions.assertTrue(getConfigVersionResult.isEmpty());
        setResult = redisConfigService.setConfig(namespace, testConfigId, "getConfigVersionData-1").block();
        Assertions.assertTrue(setResult);
        getConfigVersionResult = redisConfigService.getConfigVersions(namespace, testConfigId).block();
        Assertions.assertFalse(getConfigVersionResult.isEmpty());
        var configVersion = getConfigVersionResult.get(0);
        Assertions.assertEquals(testConfigId, configVersion.getConfigId());
        Assertions.assertEquals(1, configVersion.getVersion());
    }

    @Test
    void getConfigVersionsLast10() {
        clearTestData(namespace);
        for (int i = 0; i < ConfigRollback.HISTORY_SIZE * 2 + 1; i++) {
            var setResult = redisConfigService.setConfig(namespace, testConfigId, "getConfigVersionData-" + i).block();
        }
        var getConfigVersionResult = redisConfigService.getConfigVersions(namespace, testConfigId).block();
        Assertions.assertFalse(getConfigVersionResult.isEmpty());
        Assertions.assertEquals(ConfigRollback.HISTORY_SIZE, getConfigVersionResult.size());
        var configVersion = getConfigVersionResult.get(0);
        Assertions.assertEquals(testConfigId, configVersion.getConfigId());
        Assertions.assertEquals(ConfigRollback.HISTORY_SIZE * 2, configVersion.getVersion());
    }

    @Test
    void getConfigHistory() {
        getConfigVersions();
        var config = redisConfigService.getConfigHistory(namespace, testConfigId, 1).block();
        Assertions.assertNotNull(config);
        Assertions.assertEquals(testConfigId, config.getConfigId());
        Assertions.assertEquals(1, config.getVersion());
    }
}
