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

package me.ahoo.cosky.core.redis;

import me.ahoo.cosky.core.NamespaceService;
import me.ahoo.cosky.core.test.AbstractReactiveRedisTest;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.test.StepVerifier;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author ahoo wang
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisNamespaceServiceTest extends AbstractReactiveRedisTest {
    NamespaceService namespaceService;
    
    @BeforeEach
    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        namespaceService = new RedisNamespaceService(redisTemplate);
    }
    
    @AfterEach
    @Override
    public void destroy() {
        super.destroy();
    }
    
    @Test
    void getNamespaces() {
        String ns = UUID.randomUUID().toString();
        StepVerifier
            .create(
                namespaceService
                    .setNamespace(ns).
                    then(namespaceService.getNamespaces().collect(Collectors.toSet()))
            )
            .expectNextMatches((namespaces) -> namespaces.contains(ns))
            .verifyComplete();
    }
    
    @Test
    void setNamespace() {
        String ns = UUID.randomUUID().toString();
        StepVerifier
            .create(namespaceService.setNamespace(ns))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
    }
    
    @Test
    void removeNamespace() {
        String ns = UUID.randomUUID().toString();
        StepVerifier
            .create(
                namespaceService
                    .setNamespace(ns)
                    .then(namespaceService.removeNamespace(ns))
            )
            .expectNext(Boolean.TRUE)
            .verifyComplete();
    }
    
}
