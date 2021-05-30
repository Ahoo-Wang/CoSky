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
        redisConfigService = new RedisConfigService(redisConnection.async());
    }

    @Test
    public void setConfig() {
        clearTestData(namespace);
        var setResult = redisConfigService.setConfig(namespace, testConfigId, "setConfigData").join();
        Assertions.assertTrue(setResult);
    }


    @Test
    public void removeConfig() {
        clearTestData(namespace);
        redisConfigService.setConfig(namespace, testConfigId, "removeConfigData").join();
        var result = redisConfigService.removeConfig(namespace, testConfigId).join();
        Assertions.assertTrue(result);
    }

    @Test
    public void getConfig() {
        clearTestData(namespace);
        var getConfigData = "getConfigData";
        var setResult = redisConfigService.setConfig(namespace, testConfigId, getConfigData).join();
        Assertions.assertTrue(setResult);
        var getResult = redisConfigService.getConfig(namespace, testConfigId).join();
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
        var setResult = redisConfigService.setConfig(namespace, test_get_config, version1Data).join();
        Assertions.assertTrue(setResult);
        var version1Config = redisConfigService.getConfig(namespace, test_get_config).join();
        Assertions.assertNotNull(version1Config);
        Assertions.assertEquals(version1Data, version1Config.getData());

        var version2Data = "version-2";
        setResult = redisConfigService.setConfig(namespace, test_get_config, version2Data).join();
        Assertions.assertTrue(setResult);
        var version2Config = redisConfigService.getConfig(namespace, test_get_config).join();
        Assertions.assertNotNull(version2Config);
        Assertions.assertEquals(version2Data, version2Config.getData());

        var rollbackResult = redisConfigService.rollback(namespace, test_get_config, version1Config.getVersion()).join();
        Assertions.assertTrue(rollbackResult);

        var afterRollbackConfig = redisConfigService.getConfig(namespace, test_get_config).join();

        Assertions.assertEquals(version1Config.getData(), afterRollbackConfig.getData());
    }

    @Test
    void getConfigs() {
        clearTestData(namespace);
        var getResult = redisConfigService.getConfigs(namespace).join();
        Assertions.assertTrue(getResult.isEmpty());
        var setResult = redisConfigService.setConfig(namespace, testConfigId, "getConfigsData").join();
        Assertions.assertTrue(setResult);
        getResult = redisConfigService.getConfigs(namespace).join();
        Assertions.assertFalse(getResult.isEmpty());
        Assertions.assertEquals(testConfigId, getResult.iterator().next());
    }

    @Test
    void getConfigVersions() {
        clearTestData(namespace);
        var setResult = redisConfigService.setConfig(namespace, testConfigId, "getConfigVersionData").join();
        Assertions.assertTrue(setResult);
        var getConfigVersionResult = redisConfigService.getConfigVersions(namespace, testConfigId).join();
        Assertions.assertTrue(getConfigVersionResult.isEmpty());
        setResult = redisConfigService.setConfig(namespace, testConfigId, "getConfigVersionData-1").join();
        Assertions.assertTrue(setResult);
        getConfigVersionResult = redisConfigService.getConfigVersions(namespace, testConfigId).join();
        Assertions.assertFalse(getConfigVersionResult.isEmpty());
        var configVersion = getConfigVersionResult.get(0);
        Assertions.assertEquals(testConfigId, configVersion.getConfigId());
        Assertions.assertEquals(1, configVersion.getVersion());
    }

    @Test
    void getConfigVersionsLast10() {
        clearTestData(namespace);
        for (int i = 0; i < ConfigRollback.HISTORY_SIZE * 2 + 1; i++) {
            var setResult = redisConfigService.setConfig(namespace, testConfigId, "getConfigVersionData-" + i).join();
        }
        var getConfigVersionResult = redisConfigService.getConfigVersions(namespace, testConfigId).join();
        Assertions.assertFalse(getConfigVersionResult.isEmpty());
        Assertions.assertEquals(ConfigRollback.HISTORY_SIZE, getConfigVersionResult.size());
        var configVersion = getConfigVersionResult.get(0);
        Assertions.assertEquals(testConfigId, configVersion.getConfigId());
        Assertions.assertEquals(ConfigRollback.HISTORY_SIZE * 2, configVersion.getVersion());
    }

    @Test
    void getConfigHistory() {
        getConfigVersions();
        var config = redisConfigService.getConfigHistory(namespace, testConfigId, 1).join();
        Assertions.assertNotNull(config);
        Assertions.assertEquals(testConfigId, config.getConfigId());
        Assertions.assertEquals(1, config.getVersion());
    }
}
