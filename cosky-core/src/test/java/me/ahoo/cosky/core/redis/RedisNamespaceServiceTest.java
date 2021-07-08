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

package me.ahoo.cosky.core.redis;

import io.lettuce.core.RedisClient;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.cosky.core.NamespaceService;
import me.ahoo.cosky.core.TestRedisClient;
import org.junit.jupiter.api.*;

import java.util.Objects;
import java.util.UUID;

/**
 * @author ahoo wang
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisNamespaceServiceTest {
    NamespaceService namespaceService;
    private RedisClient redisClient;

    @BeforeAll
    private void init() {
        redisClient = TestRedisClient.createClient();
        namespaceService = new RedisNamespaceService(redisClient.connect().async());
    }

    @Test
    void getNamespaces() {
        var namespaces = namespaceService.getNamespaces().join();
        Assertions.assertNotNull(namespaces);
    }

    @Test
    void setNamespace() {
        var ns = UUID.randomUUID().toString();
        namespaceService.removeNamespace(ns).join();
        var isOk = namespaceService.setNamespace(ns).join();
        Assertions.assertTrue(isOk);
    }

    @Test
    void removeNamespace() {
        var ns = UUID.randomUUID().toString();
        namespaceService.setNamespace(ns).join();
        var isOk = namespaceService.removeNamespace(ns).join();
        Assertions.assertTrue(isOk);
    }

    @AfterAll
    private void destroy() {
        if (Objects.nonNull(redisClient)) {
            redisClient.shutdown();
        }
    }
}
