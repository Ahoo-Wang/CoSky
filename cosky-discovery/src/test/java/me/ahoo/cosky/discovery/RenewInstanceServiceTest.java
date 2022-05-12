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

import lombok.SneakyThrows;

import me.ahoo.cosky.core.test.AbstractReactiveRedisTest;
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

/**
 * @author ahoo wang
 */
public class RenewInstanceServiceTest  extends AbstractReactiveRedisTest {
    private final static String namespace = "test_renew";
    private ServiceInstance testInstance;
    private ServiceInstance testFixedInstance;
    private RedisServiceRegistry redisServiceRegistry;
    private RenewInstanceService renewService;
    
    @BeforeEach
    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        testInstance = TestServiceInstance.randomInstance();
        testFixedInstance = TestServiceInstance.randomFixedInstance();
        RegistryProperties registryProperties = new RegistryProperties();
        registryProperties.setInstanceTtl(15);
        redisServiceRegistry = new RedisServiceRegistry(registryProperties, redisTemplate);
        RenewProperties renewProperties = new RenewProperties();
        renewService = new RenewInstanceService(renewProperties, redisServiceRegistry);
    }
    
    @AfterEach
    @Override
    public void destroy() {
        renewService.stop();
        super.destroy();
    }
    
    @SneakyThrows
    @Test
    public void start() {
        renewService.start();
        redisServiceRegistry.register(namespace, testInstance).block();
        redisServiceRegistry.register(namespace, testFixedInstance).block();
        TimeUnit.SECONDS.sleep(20);
    }
}
