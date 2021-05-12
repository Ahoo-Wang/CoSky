package me.ahoo.govern.config.redis;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.govern.config.ConfigChangedListener;
import me.ahoo.govern.config.NamespacedConfigId;
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
    private final String namespace = "test_cfg_csy";

    @BeforeEach
    private void init() {
        var redisConfigService = new RedisConfigService(redisConnection.async());
        consistencyRedisConfigService = new ConsistencyRedisConfigService(redisConfigService, new RedisMessageListenable(redisClient.connectPubSub()));
    }

    @Test
    void getConfig() {
        clearTestData(namespace);
        var getConfigData = "getConfigData";
        var setResult = consistencyRedisConfigService.setConfig(namespace, testConfigId, getConfigData).join();
        Assertions.assertTrue(setResult);
        var getResult = consistencyRedisConfigService.getConfig(namespace, testConfigId).join();
        Assertions.assertNotNull(getResult);
        Assertions.assertEquals(testConfigId, getResult.getConfigId());
        Assertions.assertEquals(getConfigData, getResult.getData());
        Assertions.assertEquals(1, getResult.getVersion());
        var getResult2 = consistencyRedisConfigService.getConfig(namespace, testConfigId).join();
        Assertions.assertTrue(getResult2 == getResult);
    }

    private final static int SLEEP_FOR_WAIT_MESSAGE = 1;

    @SneakyThrows
    protected void sleepForWaitNotify() {
        TimeUnit.SECONDS.sleep(SLEEP_FOR_WAIT_MESSAGE);
    }

    @Test
    void getConfigChanged() {
        clearTestData(namespace);
        var getResult = consistencyRedisConfigService.getConfig(namespace, testConfigId).join();
        Assertions.assertNull(getResult);
        var getConfigData = "getConfigData";
        var setResult = consistencyRedisConfigService.setConfig(namespace, testConfigId, getConfigData).join();
        Assertions.assertTrue(setResult);
        sleepForWaitNotify();
        getResult = consistencyRedisConfigService.getConfig(namespace, testConfigId).join();
        Assertions.assertNotNull(getResult);
        var getConfigData2 = "getConfigData-2";
        setResult = consistencyRedisConfigService.setConfig(namespace, testConfigId, getConfigData2).join();
        Assertions.assertTrue(setResult);
        sleepForWaitNotify();
        var getResult2 = consistencyRedisConfigService.getConfig(namespace, testConfigId).join();
        Assertions.assertEquals(getConfigData2, getResult2.getData());
        Assertions.assertNotEquals(getResult, getResult2);
    }

    @Test
    void getConfigChangedRemove() {
        clearTestData(namespace);
        var getResult = consistencyRedisConfigService.getConfig(namespace, testConfigId).join();
        Assertions.assertNull(getResult);
        var getConfigData = "getConfigChangedRemoveData";
        var setResult = consistencyRedisConfigService.setConfig(namespace, testConfigId, getConfigData).join();
        Assertions.assertTrue(setResult);
        sleepForWaitNotify();
        getResult = consistencyRedisConfigService.getConfig(namespace, testConfigId).join();
        Assertions.assertNotNull(getResult);
        var removeResult = consistencyRedisConfigService.removeConfig(namespace, testConfigId).join();
        Assertions.assertTrue(removeResult);
        sleepForWaitNotify();
        var getResult2 = consistencyRedisConfigService.getConfig(namespace, testConfigId).join();
        Assertions.assertNull(getResult2);
    }

    @Test
    void getConfigChangedRollback() {
        clearTestData(namespace);
        var version1Data = "version-1";
        var setResult = consistencyRedisConfigService.setConfig(namespace, testConfigId, version1Data).join();
        Assertions.assertTrue(setResult);
        var version1Config = consistencyRedisConfigService.getConfig(namespace, testConfigId).join();
        Assertions.assertNotNull(version1Config);
        Assertions.assertEquals(version1Data, version1Config.getData());

        var version2Data = "version-2";
        setResult = consistencyRedisConfigService.setConfig(namespace, testConfigId, version2Data).join();
        Assertions.assertTrue(setResult);
        sleepForWaitNotify();
        var version2Config = consistencyRedisConfigService.getConfig(namespace, testConfigId).join();
        Assertions.assertNotNull(version2Config);
        Assertions.assertEquals(version2Data, version2Config.getData());

        var rollbackResult = consistencyRedisConfigService.rollback(namespace, testConfigId, version1Config.getVersion()).join();
        Assertions.assertTrue(rollbackResult);
        sleepForWaitNotify();
        var afterRollbackConfig = consistencyRedisConfigService.getConfig(namespace, testConfigId).join();

        Assertions.assertEquals(version1Config.getData(), afterRollbackConfig.getData());
    }

    @SneakyThrows
    @Test
    void addListener() {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AtomicReference<NamespacedConfigId> changedConfigId = new AtomicReference<>();
        var testConfig = NamespacedConfigId.of(namespace, testConfigId);
        var changedListener = new ConfigChangedListener() {
            @Override
            public void onChange(NamespacedConfigId namespacedConfigId, String op) {
                log.warn("addListener@Test - configId:[{}] - message:[{}]", namespacedConfigId, op);
                changedConfigId.set(namespacedConfigId);
                countDownLatch.countDown();
            }
        };
        var addListenerResult = consistencyRedisConfigService.addListener(testConfig, changedListener);
        Assertions.assertTrue(addListenerResult.join());
        getConfigChanged();
        countDownLatch.await();
        Assertions.assertEquals(testConfig, changedConfigId.get());
    }

    @Test
    void removeListener() {
        var testConfig = NamespacedConfigId.of(namespace, testConfigId);
        var changedListener = new ConfigChangedListener() {
            @Override
            public void onChange(NamespacedConfigId namespacedConfigId, String op) {
                Assertions.fail();
            }
        };
        var addListenerResult = consistencyRedisConfigService.addListener(testConfig, changedListener);

        Assertions.assertTrue(addListenerResult.join());
        var removeListenerResult = consistencyRedisConfigService.removeListener(testConfig, changedListener);
        Assertions.assertTrue(removeListenerResult.join());
        getConfigChanged();
    }
}
