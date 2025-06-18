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
package me.ahoo.cosky.discovery.loadbalancer

import me.ahoo.cosid.test.MockIdGenerator
import me.ahoo.cosky.discovery.InstanceEventListenerContainer
import me.ahoo.cosky.discovery.RegistryProperties
import me.ahoo.cosky.discovery.TestServiceInstance.createInstance
import me.ahoo.cosky.discovery.TestServiceInstance.registerRandomInstanceAndTestThenDeregister
import me.ahoo.cosky.discovery.redis.RedisInstanceEventListenerContainer
import me.ahoo.cosky.discovery.redis.RedisServiceDiscovery
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry
import me.ahoo.cosky.test.AbstractReactiveRedisTest
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.kotlin.test.test
import java.util.*

/**
 * @author ahoo wang
 */
internal class RandomLoadBalancerTest : AbstractReactiveRedisTest() {
    private lateinit var redisServiceDiscovery: RedisServiceDiscovery
    private lateinit var redisServiceRegistry: RedisServiceRegistry
    private lateinit var randomLoadBalancer: RandomLoadBalancer
    private lateinit var instanceEventListenerContainer: InstanceEventListenerContainer
    override fun afterInitializedRedisClient() {
        val registryProperties = RegistryProperties()
        redisServiceRegistry = RedisServiceRegistry(registryProperties, redisTemplate)
        redisServiceDiscovery = RedisServiceDiscovery(redisTemplate)
        instanceEventListenerContainer =
            RedisInstanceEventListenerContainer(ReactiveRedisMessageListenerContainer(connectionFactory))
        randomLoadBalancer = RandomLoadBalancer(redisServiceDiscovery, instanceEventListenerContainer)
    }

    @Test
    fun chooseNone() {
        randomLoadBalancer.choose(namespace, UUID.randomUUID().toString())
            .test()
            .expectNextCount(0)
            .verifyComplete()
    }

    @Test
    fun chooseOne() {
        registerRandomInstanceAndTestThenDeregister(namespace, redisServiceRegistry) {
            randomLoadBalancer.choose(namespace, it.serviceId)
                .test()
                .expectNextMatches { serviceInstance ->
                    serviceInstance.assert().isEqualTo(it)
                    true
                }
        }
    }

    @Test
    fun chooseMultiple() {
        val serviceId = MockIdGenerator.INSTANCE.generateAsString()
        val instance1 = createInstance(serviceId)
        val instance2 = createInstance(serviceId)
        val instance3 = createInstance(serviceId)
        redisServiceRegistry.register(namespace, instance1).block()
        redisServiceRegistry.register(namespace, instance2).block()
        redisServiceRegistry.register(namespace, instance3).block()
        val expectedInstance = randomLoadBalancer.choose(namespace, serviceId).block()
        expectedInstance.assert().isNotNull()
        requireNotNull(expectedInstance)
        val succeeded =
            expectedInstance.instanceId == instance1.instanceId || expectedInstance.instanceId == instance2.instanceId || expectedInstance.instanceId == instance3.instanceId
        Assertions.assertTrue(succeeded)
        redisServiceRegistry.deregister(namespace, instance1).block()
        redisServiceRegistry.deregister(namespace, instance2).block()
        redisServiceRegistry.deregister(namespace, instance3).block()
    }

    companion object {
        private const val namespace = "test_lb"
    }
}
