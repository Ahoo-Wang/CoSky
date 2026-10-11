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

import me.ahoo.cosid.test.MockIdGenerator
import me.ahoo.cosky.discovery.redis.RedisServiceTopology
import me.ahoo.cosky.test.AbstractReactiveRedisTest
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import reactor.kotlin.test.test

/**
 * @author ahoo wang
 */
class ServiceTopologyTest : AbstractReactiveRedisTest() {

    private fun topologyOf(namespace: String, consumer: String): ServiceTopology {
        return RedisServiceTopology(redisTemplate, NamespacedServiceId(namespace, consumer))
    }

    @Test
    fun addAndGetTopology() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        val otherNamespace = MockIdGenerator.INSTANCE.generateAsString()
        val order = topologyOf(namespace, "order")
        order.addTopology(namespace, "user").test().verifyComplete()
        order.addTopology(otherNamespace, "payment").test().verifyComplete()
        topologyOf(namespace, "user").addTopology(namespace, "account").test().verifyComplete()

        order.getTopology(namespace)
            .test()
            .expectNextMatches {
                it.assert().isEqualTo(
                    mapOf(
                        "order" to setOf("user", "$otherNamespace.payment"),
                        "user" to setOf("account"),
                    ),
                )
                true
            }
            .verifyComplete()
    }

    @Test
    fun selfDependencyIsIgnored() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        val order = topologyOf(namespace, "order")
        order.addTopology(namespace, "order").test().verifyComplete()

        order.getTopology(namespace).test().expectNext(emptyMap()).verifyComplete()
    }
}
