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
package me.ahoo.cosky.discovery

import me.ahoo.cosky.core.redis.LuaScripts
import me.ahoo.cosky.test.AbstractReactiveRedisTest
import org.junit.jupiter.api.Test
import reactor.kotlin.test.test

/**
 * The key functions of the discovery Lua scripts must produce exactly the keys of [DiscoveryKeyGenerator].
 */
class DiscoveryLuaKeysTest : AbstractReactiveRedisTest() {

    @Test
    fun keys() {
        val namespace = "cosky-{ns}"
        val serviceId = "order-service"
        val instanceId = "order-service@http#127.0.0.1#8080"
        val consumer = "gateway"
        val script = LuaScripts.load("lua-test/discovery_keys.lua", List::class.java)
        redisTemplate.execute(script, emptyList(), listOf(namespace, serviceId, instanceId, consumer))
            .test()
            .expectNext(
                listOf(
                    DiscoveryKeyGenerator.getInstanceIdxKey(namespace, serviceId),
                    DiscoveryKeyGenerator.getInstanceKey(namespace, instanceId),
                    DiscoveryKeyGenerator.getTopologyKey(namespace, consumer),
                ),
            )
            .verifyComplete()
    }
}
