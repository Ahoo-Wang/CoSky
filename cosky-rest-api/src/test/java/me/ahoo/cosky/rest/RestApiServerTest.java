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

package me.ahoo.cosky.rest;

import me.ahoo.cosky.spring.cloud.CoSkyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author ahoo wang
 */
@SpringBootTest({
    "spring.cloud.bootstrap.enabled=true",
    "cosky.security.key=test-only-jwt-secret-0123456789abcdef"
})
class RestApiServerTest {
    @Autowired
    private CoSkyProperties coSkyProperties;

    @Test
    void contextLoads() {
    }

    /**
     * The namespace configured in bootstrap.yaml must survive Spring Cloud's properties rebinding.
     */
    @Test
    void namespaceFromBootstrapConfiguration() {
        assertEquals("cosky-{system}", coSkyProperties.getNamespace());
    }

}
