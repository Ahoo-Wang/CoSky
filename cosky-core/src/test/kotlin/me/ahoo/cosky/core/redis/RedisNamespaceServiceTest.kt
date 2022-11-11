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
package me.ahoo.cosky.core.redis

import me.ahoo.cosid.test.MockIdGenerator
import me.ahoo.cosky.core.NamespaceService
import me.ahoo.cosky.test.AbstractReactiveRedisTest
import org.junit.jupiter.api.Test
import reactor.kotlin.test.test

/**
 * @author ahoo wang
 */

internal class RedisNamespaceServiceTest : AbstractReactiveRedisTest() {
    lateinit var namespaceService: NamespaceService

    override fun afterInitializedRedisClient() {
        namespaceService = RedisNamespaceService(redisTemplate)
    }

    @Test
    fun getNamespaces() {
        val ns: String = MockIdGenerator.INSTANCE.generateAsString()
        namespaceService
            .setNamespace(ns)
            .thenMany(namespaceService.namespaces.collectList())
            .test()
            .consumeNextWith { it.contains(ns) }
            .verifyComplete()
    }

    @Test
    fun setNamespace() {
        val ns: String = MockIdGenerator.INSTANCE.generateAsString()
        namespaceService
            .setNamespace(ns)
            .test()
            .expectNext(java.lang.Boolean.TRUE)
            .verifyComplete()
    }

    @Test
    fun removeNamespace() {
        val ns: String = MockIdGenerator.INSTANCE.generateAsString()
        namespaceService
            .setNamespace(ns)
            .then(namespaceService.removeNamespace(ns))
            .test()
            .expectNext(java.lang.Boolean.TRUE)
            .verifyComplete()
    }
}
