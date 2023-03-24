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
            redisServiceRegistry,
        ) {
            redisServiceDiscovery.getServices(namespace).collectList()
                .test()
                .expectNextMatches { services -> services.contains(it.serviceId) }
                .verifyComplete()
        }
    }

    @Test
    fun getInstances() {
        registerRandomInstanceAndTestThenDeregister(
            namespace,
            redisServiceRegistry,
        ) {
            redisServiceDiscovery.getInstances(namespace, it.serviceId)
                .test()
                .expectNext(it)
                .verifyComplete()
        }
    }

    @Test
    fun getInstance() {
        registerRandomInstanceAndTestThenDeregister(
            namespace,
            redisServiceRegistry,
        ) {
            redisServiceDiscovery.getInstance(namespace, it.serviceId, it.instanceId)
                .test()
                .expectNext(it)
                .verifyComplete()
        }
    }

    @Test
    fun getInstanceNone() {
        redisServiceDiscovery.getInstance(
            namespace = namespace,
            serviceId = MockIdGenerator.INSTANCE.generateAsString(),
            instanceId = MockIdGenerator.INSTANCE.generateAsString(),
        )
            .test()
            .verifyComplete()
    }

    @Test
    fun getInstanceTtl() {
        registerRandomInstanceAndTestThenDeregister(
            namespace,
            redisServiceRegistry,
        ) {
            redisServiceDiscovery.getInstanceTtl(namespace, it.serviceId, it.instanceId)
                .test()
                .expectNextMatches {
                    assertThat(it, greaterThan(0L))
                    true
                }
                .verifyComplete()
        }
    }

    @Test
    fun getFixedInstance() {
        val fixedInstance = TestServiceInstance.randomFixedInstance()
        redisServiceRegistry.register(namespace, fixedInstance)
            .test()
            .expectNext(true)
            .verifyComplete()

        redisServiceDiscovery.getInstance(namespace, fixedInstance.serviceId, fixedInstance.instanceId)
            .test()
            .expectNextMatches {
                assertThat(it, equalTo(fixedInstance))
                true
            }
            .verifyComplete()

        redisServiceDiscovery.getInstances(namespace, fixedInstance.serviceId)
            .test()
            .expectNextMatches {
                assertThat(it, equalTo(fixedInstance))
                true
            }
            .verifyComplete()

        redisServiceDiscovery.getInstanceTtl(namespace, fixedInstance.serviceId, fixedInstance.instanceId)
            .test()
            .expectNextMatches {
                assertThat(it, equalTo(-1L))
                true
            }
            .verifyComplete()
    }

    companion object {
        private const val namespace = "test_svc"
    }
}
