/*
 *
 *  * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package me.ahoo.cosky.discovery;

import lombok.SneakyThrows;
import lombok.var;
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/**
 * @author ahoo wang
 */
public class RedisServiceRegistryTest extends BaseOnRedisClientTest {
    private final static String namespace = "test_svc_csy";
    private ServiceInstance testInstance;
    private ServiceInstance testFixedInstance;
    private RedisServiceRegistry redisServiceRegistry;

    @BeforeAll
    private void init() {
        testInstance = TestServiceInstance.TEST_INSTANCE;
        testFixedInstance = TestServiceInstance.TEST_FIXED_INSTANCE;
        var registryProperties = new RegistryProperties();
        registryProperties.setInstanceTtl(10);

        redisServiceRegistry = new RedisServiceRegistry(registryProperties, redisConnection.async());
    }

    @Test
    public void register() {
        clearTestData(namespace);
        var result = redisServiceRegistry.register(namespace, testInstance).join();
        Assertions.assertTrue(result);
    }

    @Test
    public void renew() {
        var result = redisServiceRegistry.renew(namespace, testInstance).join();
        Assertions.assertTrue(result);
    }

    @Test
    public void renewFixed() {
        var result = redisServiceRegistry.renew(namespace, testFixedInstance).join();
        Assertions.assertFalse(result);
    }

    @Test
    public void registerFixed() {
        var result = redisServiceRegistry.register(namespace, testFixedInstance).join();
        Assertions.assertTrue(result);
    }


    @Test
    public void deregister() {
        redisServiceRegistry.deregister(namespace, testInstance).join();
    }

    private final static int REPEATED_SIZE = 60000;

    @Test
    public void registerRepeatedSync() {
        for (int i = 0; i < 20; i++) {
            redisServiceRegistry.register(namespace, testInstance).join();
        }
    }

    @SneakyThrows
//    @Test
    public void registerRepeatedAsync() {
        var futures = new CompletableFuture[REPEATED_SIZE];
        for (int i = 0; i < REPEATED_SIZE; i++) {
            var future = redisServiceRegistry.register(testInstance);
            futures[i] = future;
        }
        CompletableFuture.allOf(futures).join();
    }

    private final static int THREAD_COUNT = 5;

    //    @Test
    public void registerRepeatedMultiple() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(THREAD_COUNT);
        for (int thNum = 0; thNum < THREAD_COUNT; thNum++) {
            new Thread(() -> {
                registerRepeatedAsync();
                countDownLatch.countDown();
            }).start();
        }
        countDownLatch.await();
    }

    //    @Test
    public void deregisterRepeatedAsync() {
        var futures = new CompletableFuture[REPEATED_SIZE];
        for (int i = 0; i < REPEATED_SIZE; i++) {
            futures[i] = redisServiceRegistry.deregister(testInstance);
        }
        CompletableFuture.allOf(futures).join();
    }

    //    @Test
    public void deregisterRepeatedMultiple() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(THREAD_COUNT);
        for (int thNum = 0; thNum < THREAD_COUNT; thNum++) {
            new Thread(() -> {
                deregisterRepeatedAsync();
                countDownLatch.countDown();
            }).start();
        }
        countDownLatch.await();
    }
}
