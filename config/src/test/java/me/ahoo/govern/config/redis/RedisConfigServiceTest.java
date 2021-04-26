package me.ahoo.govern.config.redis;

import lombok.var;
import me.ahoo.govern.config.ConfigKeyGenerator;
import me.ahoo.govern.config.ConfigRollback;
import me.ahoo.govern.core.Consts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * @author ahoo wang
 */
public class RedisConfigServiceTest extends BaseOnRedisClientTest {
    private RedisConfigService redisConfigService;
    private final String testConfigId = "test_config";

    @BeforeAll
    private void init() {
        this.namespace="test_cfg";
        ConfigKeyGenerator keyGenerator = new ConfigKeyGenerator(namespace);
        redisConfigService = new RedisConfigService(keyGenerator, redisConnection.async());
    }

    @Test
    public void setConfig() {
        clearTestData();
        var setResult = redisConfigService.setConfig(testConfigId, "setConfigData").join();
        Assertions.assertTrue(setResult);
    }


    @Test
    public void removeConfig() {
        clearTestData();
        redisConfigService.setConfig(testConfigId, "removeConfigData").join();
        var result = redisConfigService.removeConfig(testConfigId).join();
        Assertions.assertTrue(result);
    }

    @Test
    public void getConfig() {
        clearTestData();
        var getConfigData = "getConfigData";
        var setResult = redisConfigService.setConfig(testConfigId, getConfigData).join();
        Assertions.assertTrue(setResult);
        var getResult = redisConfigService.getConfig(testConfigId).join();
        Assertions.assertNotNull(getResult);
        Assertions.assertEquals(testConfigId, getResult.getConfigId());
        Assertions.assertEquals(getConfigData, getResult.getData());
        Assertions.assertEquals(1, getResult.getVersion());
    }


    @Test
    public void rollback() {
        clearTestData();
        var test_get_config = "test_rollback_config";
        var version1Data = "version-1";
        var setResult = redisConfigService.setConfig(test_get_config, version1Data).join();
        Assertions.assertTrue(setResult);
        var version1Config = redisConfigService.getConfig(test_get_config).join();
        Assertions.assertNotNull(version1Config);
        Assertions.assertEquals(version1Data, version1Config.getData());

        var version2Data = "version-2";
        setResult = redisConfigService.setConfig(test_get_config, version2Data).join();
        Assertions.assertTrue(setResult);
        var version2Config = redisConfigService.getConfig(test_get_config).join();
        Assertions.assertNotNull(version2Config);
        Assertions.assertEquals(version2Data, version2Config.getData());

        var rollbackResult = redisConfigService.rollback(test_get_config, version1Config.getVersion()).join();
        Assertions.assertTrue(rollbackResult);

        var afterRollbackConfig = redisConfigService.getConfig(test_get_config).join();

        Assertions.assertEquals(version1Config.getData(), afterRollbackConfig.getData());
    }

    @Test
    void getConfigs() {
        clearTestData();
        var getResult = redisConfigService.getConfigs().join();
        Assertions.assertTrue(getResult.isEmpty());
        var setResult = redisConfigService.setConfig(testConfigId, "getConfigsData").join();
        Assertions.assertTrue(setResult);
        getResult = redisConfigService.getConfigs().join();
        Assertions.assertFalse(getResult.isEmpty());
        Assertions.assertEquals(testConfigId, getResult.iterator().next());
    }

    @Test
    void getConfigVersions() {
        clearTestData();
        var setResult = redisConfigService.setConfig(testConfigId, "getConfigVersionData").join();
        Assertions.assertTrue(setResult);
        var getConfigVersionResult = redisConfigService.getConfigVersions(testConfigId).join();
        Assertions.assertTrue(getConfigVersionResult.isEmpty());
        setResult = redisConfigService.setConfig(testConfigId, "getConfigVersionData-1").join();
        Assertions.assertTrue(setResult);
        getConfigVersionResult = redisConfigService.getConfigVersions(testConfigId).join();
        Assertions.assertFalse(getConfigVersionResult.isEmpty());
        var configVersion = getConfigVersionResult.get(0);
        Assertions.assertEquals(testConfigId, configVersion.getConfigId());
        Assertions.assertEquals(1, configVersion.getVersion());
    }

    @Test
    void getConfigVersionsLast10() {
        clearTestData();
        for (int i = 0; i < ConfigRollback.HISTORY_SIZE * 2 + 1; i++) {
            var setResult = redisConfigService.setConfig(testConfigId, "getConfigVersionData-" + i).join();
        }
        var getConfigVersionResult = redisConfigService.getConfigVersions(testConfigId).join();
        Assertions.assertFalse(getConfigVersionResult.isEmpty());
        Assertions.assertEquals(ConfigRollback.HISTORY_SIZE, getConfigVersionResult.size());
        var configVersion = getConfigVersionResult.get(0);
        Assertions.assertEquals(testConfigId, configVersion.getConfigId());
        Assertions.assertEquals(ConfigRollback.HISTORY_SIZE * 2, configVersion.getVersion());
    }

    @Test
    void getConfigHistory() {
        getConfigVersions();
        var config = redisConfigService.getConfigHistory(testConfigId, 1).join();
        Assertions.assertNotNull(config);
        Assertions.assertEquals(testConfigId, config.getConfigId());
        Assertions.assertEquals(1, config.getVersion());
    }
}
