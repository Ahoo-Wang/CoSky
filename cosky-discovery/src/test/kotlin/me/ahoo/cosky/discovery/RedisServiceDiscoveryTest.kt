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

import me.ahoo.cosky.discovery.TestServiceInstance.registerRandomInstanceAndTestThenDeregister
import me.ahoo.cosky.discovery.redis.RedisServiceDiscovery
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry
import me.ahoo.cosky.test.AbstractReactiveRedisTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test
import reactor.kotlin.test.test

/**
 * @author ahoo wang
 */
class RedisServiceDiscoveryTest : AbstractReactiveRedisTest() {
    private lateinit var redisServiceDiscovery: RedisServiceDiscovery
    private lateinit var redisServiceRegistry: RedisServiceRegistry

    override fun afterInitializedRedisClient() {
        val registryProperties = RegistryProperties()
        redisServiceRegistry = RedisServiceRegistry(registryProperties, redisTemplate)
        redisServiceDiscovery = RedisServiceDiscovery(redisTemplate)
    }

    @Test
    fun getServices() {
        registerRandomInstanceAndTestThenDeregister(
            namespace,
            redisServiceRegistry
        ) {
            redisServiceDiscovery.getServices(namespace).collectList()
                .test()
                .expectNextMatches {
                    assertThat(it, notNullValue())
                    // TODO
                    true
//                    assertThat(serviceIds, hasItem(it.serviceId))
                }
        }
    }

    @Test
    fun getInstances() {
        registerRandomInstanceAndTestThenDeregister(
            namespace,
            redisServiceRegistry
        ) {
            val instances =
                redisServiceDiscovery.getInstances(namespace, it.serviceId).collectList().block()
            assertThat(instances, notNullValue())
            requireNotNull(instances)
            assertThat(instances.size, equalTo(1))
            val expectedInstance = instances.first()
            assertThat(expectedInstance, equalTo(it))
        }
    }

    @Test
    fun getInstance() {
        registerRandomInstanceAndTestThenDeregister(
            namespace,
            redisServiceRegistry
        ) { instance: ServiceInstance ->
            val actualInstance =
                redisServiceDiscovery.getInstance(namespace, instance.serviceId, instance.instanceId).block()
            requireNotNull(actualInstance)
            assertThat(actualInstance, equalTo(instance))
        }
    }

    companion object {
        private const val namespace = "test_svc"
    }
}
