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

package me.ahoo.cosky.discovery;

import lombok.SneakyThrows;
import lombok.var;
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

/**
 * @author ahoo wang
 */
public class RenewInstanceServiceTest extends BaseOnRedisClientTest {
    private final static String namespace = "test_renew";
    private ServiceInstance testInstance;
    private ServiceInstance testFixedInstance;
    private RedisServiceRegistry redisServiceRegistry;
    private RenewInstanceService renewService;

    @BeforeAll
    private void init() {
        testInstance = TestServiceInstance.TEST_INSTANCE;
        testFixedInstance = TestServiceInstance.TEST_FIXED_INSTANCE;
        var registryProperties = new RegistryProperties();
        registryProperties.setInstanceTtl(15);
        redisServiceRegistry = new RedisServiceRegistry(registryProperties, redisConnection.async());
        var renewProperties = new RenewProperties();
        renewService = new RenewInstanceService(renewProperties, redisServiceRegistry);
    }

    @SneakyThrows
    @Test
    public void start() {
        renewService.start();
        redisServiceRegistry.register(namespace, testInstance);
        redisServiceRegistry.register(namespace, testFixedInstance);
        TimeUnit.SECONDS.sleep(20);
    }

    @AfterAll
    public void stop() {
        renewService.stop();
    }

}
