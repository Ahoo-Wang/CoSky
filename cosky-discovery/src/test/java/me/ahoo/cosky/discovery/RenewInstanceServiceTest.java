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

import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosky.core.test.AbstractReactiveRedisTest;
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author ahoo wang
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RenewInstanceServiceTest extends AbstractReactiveRedisTest {
    private ServiceInstance testInstance;
    private ServiceInstance testFixedInstance;
    private RedisServiceRegistry redisServiceRegistry;
    private final RenewProperties renewProperties = new RenewProperties();
    
    @BeforeAll
    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        testInstance = TestServiceInstance.randomInstance();
        testFixedInstance = TestServiceInstance.randomFixedInstance();
        RegistryProperties registryProperties = new RegistryProperties();
        registryProperties.setInstanceTtl(Duration.ofSeconds(15));
        redisServiceRegistry = new RedisServiceRegistry(registryProperties, redisTemplate);
    }
    
    @AfterAll
    @Override
    public void destroy() {
        super.destroy();
    }
    
    @SneakyThrows
    @Test
    public void start() {
        final String namespace = MockIdGenerator.INSTANCE.generateAsString();
        Semaphore semaphore = new Semaphore(0);
        RenewInstanceService renewService = new RenewInstanceService(renewProperties, redisServiceRegistry, (serviceInstance -> {
            Assertions.assertEquals(testInstance, serviceInstance);
            semaphore.release();
        }));
        renewService.start();
        StepVerifier.create(redisServiceRegistry.register(namespace, testInstance))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
        StepVerifier.create(redisServiceRegistry.register(namespace, testFixedInstance))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
        Assertions.assertTrue(semaphore.tryAcquire(renewProperties.getInitialDelay().plusSeconds(2).getSeconds(), TimeUnit.SECONDS));
        renewService.stop();
    }
}
