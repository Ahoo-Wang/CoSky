package me.ahoo.govern.config.redis;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.govern.config.ConfigKeyGenerator;
import me.ahoo.govern.core.listener.RedisMessageListenable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author ahoo wang
 */
@Slf4j
class ConsistencyRedisConfigServiceTest extends BaseOnRedisClientTest {

    private ConsistencyRedisConfigService consistencyRedisConfigService;
    private final String testConfigId = "test_config";

    @BeforeEach
    private void init() {
        this.namespace = "test_cfg_csy";
        ConfigKeyGenerator keyGenerator = new ConfigKeyGenerator(namespace);
        var redisConfigService = new RedisConfigService(keyGenerator, redisConnection.async());
        consistencyRedisConfigService = new ConsistencyRedisConfigService(keyGenerator, redisConfigService, new RedisMessageListenable(redisClient.connectPubSub()));
    }

    @Test
    void getConfig() {
        clearTestData();
        var getConfigData = "getConfigData";
        var setResult = consistencyRedisConfigService.setConfig(testConfigId, getConfigData).join();
        Assertions.assertTrue(setResult);
        var getResult = consistencyRedisConfigService.getConfig(testConfigId).join();
        Assertions.assertNotNull(getResult);
        Assertions.assertEquals(testConfigId, getResult.getConfigId());
        Assertions.assertEquals(getConfigData, getResult.getData());
        Assertions.assertEquals(1, getResult.getVersion());
        var getResult2 = consistencyRedisConfigService.getConfig(testConfigId).join();
        Assertions.assertTrue(getResult2 == getResult);
    }

    private final static int SLEEP_FOR_WAIT_MESSAGE = 1;

    @SneakyThrows
    protected void sleepForWaitNotify() {
        TimeUnit.SECONDS.sleep(SLEEP_FOR_WAIT_MESSAGE);
    }

    @Test
    void getConfigChanged() {
        clearTestData();
        var getResult = consistencyRedisConfigService.getConfig(testConfigId).join();
        Assertions.assertNull(getResult);
        var getConfigData = "getConfigData";
        var setResult = consistencyRedisConfigService.setConfig(testConfigId, getConfigData).join();
        Assertions.assertTrue(setResult);
        sleepForWaitNotify();
        getResult = consistencyRedisConfigService.getConfig(testConfigId).join();
        Assertions.assertNotNull(getResult);
        var getConfigData2 = "getConfigData-2";
        setResult = consistencyRedisConfigService.setConfig(testConfigId, getConfigData2).join();
        Assertions.assertTrue(setResult);
        sleepForWaitNotify();
        var getResult2 = consistencyRedisConfigService.getConfig(testConfigId).join();
        Assertions.assertEquals(getConfigData2, getResult2.getData());
        Assertions.assertNotEquals(getResult, getResult2);
    }

    @Test
    void getConfigChangedRemove() {
        clearTestData();
        var getResult = consistencyRedisConfigService.getConfig(testConfigId).join();
        Assertions.assertNull(getResult);
        var getConfigData = "getConfigChangedRemoveData";
        var setResult = consistencyRedisConfigService.setConfig(testConfigId, getConfigData).join();
        Assertions.assertTrue(setResult);
        sleepForWaitNotify();
        getResult = consistencyRedisConfigService.getConfig(testConfigId).join();
        Assertions.assertNotNull(getResult);
        var removeResult = consistencyRedisConfigService.removeConfig(testConfigId).join();
        Assertions.assertTrue(removeResult);
        sleepForWaitNotify();
        var getResult2 = consistencyRedisConfigService.getConfig(testConfigId).join();
        Assertions.assertNull(getResult2);
    }

    @Test
    void getConfigChangedRollback() {
        clearTestData();
        var version1Data = "version-1";
        var setResult = consistencyRedisConfigService.setConfig(testConfigId, version1Data).join();
        Assertions.assertTrue(setResult);
        var version1Config = consistencyRedisConfigService.getConfig(testConfigId).join();
        Assertions.assertNotNull(version1Config);
        Assertions.assertEquals(version1Data, version1Config.getData());

        var version2Data = "version-2";
        setResult = consistencyRedisConfigService.setConfig(testConfigId, version2Data).join();
        Assertions.assertTrue(setResult);
        sleepForWaitNotify();
        var version2Config = consistencyRedisConfigService.getConfig(testConfigId).join();
        Assertions.assertNotNull(version2Config);
        Assertions.assertEquals(version2Data, version2Config.getData());

        var rollbackResult = consistencyRedisConfigService.rollback(testConfigId, version1Config.getVersion()).join();
        Assertions.assertTrue(rollbackResult);
        sleepForWaitNotify();
        var afterRollbackConfig = consistencyRedisConfigService.getConfig(testConfigId).join();

        Assertions.assertEquals(version1Config.getData(), afterRollbackConfig.getData());
    }

    @SneakyThrows
    @Test
    void addListener() {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AtomicReference<String> changedConfigId = new AtomicReference<>();
        var addListenerResult = consistencyRedisConfigService.addListener(testConfigId, (configId, message) -> {
            log.warn("addListener@Test - configId:[{}] - message:[{}]", configId, message);
            changedConfigId.set(configId);
            countDownLatch.countDown();
        });
        Assertions.assertTrue(addListenerResult.join());
        getConfigChanged();
        countDownLatch.await();
        Assertions.assertEquals(testConfigId, changedConfigId.get());
    }

    @Test
    void removeListener() {
        var addListenerResult = consistencyRedisConfigService.addListener(testConfigId, (configId, message) -> {
            Assertions.fail();
        });
        Assertions.assertTrue(addListenerResult.join());
        var removeListenerResult = consistencyRedisConfigService.removeListener(testConfigId);
        Assertions.assertTrue(removeListenerResult.join());
        getConfigChanged();
    }
}
