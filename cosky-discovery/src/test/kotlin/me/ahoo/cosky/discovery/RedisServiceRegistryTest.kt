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
import me.ahoo.cosky.discovery.TestServiceInstance.randomFixedInstance
import me.ahoo.cosky.discovery.TestServiceInstance.randomInstance
import me.ahoo.cosky.discovery.redis.RedisServiceDiscovery
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry
import me.ahoo.cosky.test.AbstractReactiveRedisTest
import org.junit.jupiter.api.Test
import reactor.kotlin.test.test
import java.time.Duration

/**
 * @author ahoo wang
 */
class RedisServiceRegistryTest : AbstractReactiveRedisTest() {
    private lateinit var serviceRegistry: RedisServiceRegistry
    private lateinit var serviceDiscovery: ServiceDiscovery
    override fun afterInitializedRedisClient() {
        val registryProperties = RegistryProperties(Duration.ofSeconds(10))
        serviceRegistry = RedisServiceRegistry(registryProperties, redisTemplate)
        serviceDiscovery = RedisServiceDiscovery(redisTemplate)
    }

    @Test
    fun setService() {
        serviceRegistry.setService(namespace, MockIdGenerator.INSTANCE.generateAsString())
            .test()
            .expectNext(true)
            .verifyComplete()
    }

    @Test
    fun removeService() {
        val serviceId = MockIdGenerator.INSTANCE.generateAsString()
        serviceRegistry.setService(namespace, serviceId)
            .test()
            .expectNext(true)
            .verifyComplete()
        serviceRegistry.removeService(namespace, serviceId)
            .test()
            .expectNext(true)
            .verifyComplete()
    }

    @Test
    fun register() {
        serviceRegistry.register(namespace, randomInstance())
            .test()
            .expectNext(true)
            .verifyComplete()
    }

    @Test
    fun setMetadata() {
        val instance = randomInstance()
        serviceRegistry.setMetadata(
            namespace,
            instance.serviceId,
            instanceId = instance.instanceId,
            "test",
            "test",
        )
            .test()
            .expectNext(false)
            .verifyComplete()

        serviceRegistry.register(namespace, instance)
            .test()
            .expectNext(true)
            .verifyComplete()

        serviceRegistry.setMetadata(
            namespace,
            instance.serviceId,
            instanceId = instance.instanceId,
            mapOf("test" to "testV"),
        )
            .test()
            .expectNext(true)
            .verifyComplete()

        serviceDiscovery.getInstance(namespace, instance.serviceId, instance.instanceId)
            .test()
            .expectNextMatches {
                it.metadata["test"] == "testV"
            }
            .verifyComplete()
    }

    @Test
    fun renew() {
        serviceRegistry.renew(namespace, randomInstance())
            .test()
            .expectNext(true)
            .verifyComplete()
    }

    @Test
    fun renewFixed() {
        serviceRegistry.renew(namespace, randomFixedInstance())
            .test()
            .expectNext(false)
            .verifyComplete()
    }

    @Test
    fun registerFixed() {
        serviceRegistry.register(namespace, randomFixedInstance())
            .test()
            .expectNext(true)
            .verifyComplete()
    }

    @Test
    fun deregister() {
        val testInstance = randomInstance()
        serviceRegistry.deregister(namespace, testInstance)
            .test()
            .expectNext(false)
            .verifyComplete()
        serviceRegistry.register(namespace, testInstance)
            .test()
            .expectNext(true)
            .verifyComplete()
        serviceRegistry.deregister(namespace, testInstance.serviceId, testInstance.instanceId)
            .test()
            .expectNext(true)
            .verifyComplete()
    }

    @Test
    fun registerRepeatedSync() {
        val testInstance = randomInstance()
        for (i in 0..19) {
            serviceRegistry.register(namespace, testInstance)
                .test()
                .expectNext(true)
                .verifyComplete()
        }
    }

    companion object {
        private val namespace = MockIdGenerator.INSTANCE.generateAsString()
    }
}
