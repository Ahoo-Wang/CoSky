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

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.var;
import me.ahoo.cosky.core.redis.RedisScripts;
import org.junit.jupiter.api.*;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * @author ahoo wang
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BaseOnRedisClientTest {
    protected RedisClient redisClient;
    protected StatefulRedisConnection<String, String> redisConnection;


    @BeforeAll
    private void initRedis() {
        System.out.println("--- initRedis ---");
        redisClient = RedisClient.create("redis://localhost:6379");
        redisConnection = redisClient.connect();
    }

    protected void clearTestData(String namespace) {
        RedisScripts.clearTestData(namespace, redisConnection.reactive()).block();
    }

    protected ServiceInstance createRandomInstance() {
        return createInstance(UUID.randomUUID().toString());
    }

    protected ServiceInstance createInstance(String serviceId) {
        var randomInstance = new ServiceInstance();
        randomInstance.setServiceId(serviceId);
        randomInstance.setSchema("http");
        randomInstance.setHost("127.0.0.1");
        randomInstance.setPort(ThreadLocalRandom.current().nextInt(65535));
        randomInstance.setInstanceId(InstanceIdGenerator.DEFAULT.generate(randomInstance));
        randomInstance.getMetadata().put("from", "test");
        return randomInstance;
    }


    protected void registerRandomInstanceFinal(String namespace, ServiceRegistry serviceRegistry, Consumer<ServiceInstance> doTest) {
        var randomInstance = createRandomInstance();
        serviceRegistry.register(namespace, randomInstance).block();
        doTest.accept(randomInstance);
        serviceRegistry.deregister(namespace, randomInstance).block();
    }


    @Test
    public void pingRedis() {
        System.out.println("--- pingRedis ---");
        var result = redisConnection.sync().ping();
        Assertions.assertEquals("PONG", result);
    }

    @AfterAll
    private void destroyRedis() {
        System.out.println("--- destroyRedis ---");

        if (Objects.nonNull(redisConnection)) {
            redisConnection.close();
        }
        if (Objects.nonNull(redisClient)) {
            redisClient.shutdown();
        }
    }
}
