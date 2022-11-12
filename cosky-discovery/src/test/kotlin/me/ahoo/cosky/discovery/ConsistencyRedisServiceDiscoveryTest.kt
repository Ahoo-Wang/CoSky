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
import me.ahoo.cosky.discovery.TestServiceInstance.createInstance
import me.ahoo.cosky.discovery.TestServiceInstance.registerRandomInstanceAndTestThenDeregister
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery
import me.ahoo.cosky.discovery.redis.RedisInstanceEventListenerContainer
import me.ahoo.cosky.discovery.redis.RedisServiceDiscovery
import me.ahoo.cosky.discovery.redis.RedisServiceEventListenerContainer
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry
import me.ahoo.cosky.test.AbstractReactiveRedisTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.kotlin.test.test
import reactor.test.StepVerifier
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * @author ahoo wang
 */
class ConsistencyRedisServiceDiscoveryTest : AbstractReactiveRedisTest() {
    private lateinit var delegate: RedisServiceDiscovery
    private lateinit var serviceDiscovery: ConsistencyRedisServiceDiscovery
    private lateinit var serviceRegistry: RedisServiceRegistry
    private lateinit var registryProperties: RegistryProperties
    private lateinit var serviceEventListenerContainer: ServiceEventListenerContainer
    private lateinit var instanceEventListenerContainer: InstanceEventListenerContainer
    override fun afterInitializedRedisClient() {
        registryProperties = RegistryProperties(Duration.ofSeconds(5))
        serviceRegistry = RedisServiceRegistry(registryProperties, redisTemplate)
        delegate = RedisServiceDiscovery(redisTemplate)
        serviceEventListenerContainer = createServiceEventListenerContainer()
        instanceEventListenerContainer = createInstanceEventListenerContainer()
        serviceDiscovery = ConsistencyRedisServiceDiscovery(
            delegate = delegate,
            serviceEventListenerContainer = serviceEventListenerContainer,
            instanceEventListenerContainer = instanceEventListenerContainer
        )
    }

    private fun createServiceEventListenerContainer(): ServiceEventListenerContainer =
        RedisServiceEventListenerContainer(
            ReactiveRedisMessageListenerContainer(connectionFactory)
        )

    private fun createInstanceEventListenerContainer(): InstanceEventListenerContainer =
        RedisInstanceEventListenerContainer(
            ReactiveRedisMessageListenerContainer(connectionFactory)
        )

    @Test
    fun getServices() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        registerRandomInstanceAndTestThenDeregister(
            namespace,
            serviceRegistry
        ) { instance: ServiceInstance ->
            serviceDiscovery.getServices(namespace).collectList()
                .test()
                .expectNextMatches { it.contains(instance.serviceId) }
                .verifyComplete()
        }
    }

    @Test
    fun getInstances() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        registerRandomInstanceAndTestThenDeregister(
            namespace,
            serviceRegistry
        ) { instance: ServiceInstance ->
            serviceDiscovery.getInstances(namespace, instance.serviceId).collectList()
                .test()
                .expectNextMatches { it.contains(instance) }
                .verifyComplete()
        }
    }

    @Test
    fun getInstance() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        registerRandomInstanceAndTestThenDeregister(
            namespace,
            serviceRegistry
        ) { instance: ServiceInstance ->
            serviceDiscovery.getInstance(namespace, instance.serviceId, instance.instanceId)
                .test()
                .expectNext(instance)
                .verifyComplete()
        }
    }

    @Test
    fun instanceWithCache() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        registerRandomInstanceAndTestThenDeregister(
            namespace,
            serviceRegistry
        ) { instance: ServiceInstance ->
            serviceDiscovery.getInstances(namespace, instance.serviceId).collectList()
                .test()
                .expectNextMatches { it.contains(instance) }
                .verifyComplete()
            serviceDiscovery.getInstance(namespace, instance.serviceId, instance.instanceId)
                .zipWith(serviceDiscovery.getInstance(namespace, instance.serviceId, instance.instanceId))
                .test()
                .expectNextMatches { it.t1 === it.t2 }
                .verifyComplete()
        }
    }

    @Test
    fun getServicesCache() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        val serviceId = MockIdGenerator.INSTANCE.generateAsString()
        val semaphore = Semaphore(0)
        val serviceDiscovery: ServiceDiscovery = ConsistencyRedisServiceDiscovery(
            delegate = delegate,
            serviceEventListenerContainer = serviceEventListenerContainer,
            instanceEventListenerContainer = instanceEventListenerContainer,
            hookOnResetServiceCache = {
                if (it == namespace) {
                    semaphore.release()
                }
            }
        )

        serviceDiscovery.getServices(namespace)
            .test()
            .expectNextCount(0)
            .verifyComplete()
        serviceRegistry.register(namespace, createInstance(serviceId))
            .test()
            .expectNext(true)
            .verifyComplete()
        Assertions.assertTrue(semaphore.tryAcquire(1, TimeUnit.SECONDS))
        serviceDiscovery.getServices(namespace).collectList()
            .test()
            .expectNextMatches { it.contains(serviceId) }
            .verifyComplete()
        val serviceId2 = MockIdGenerator.INSTANCE.generateAsString()
        serviceRegistry.register(namespace, createInstance(serviceId2))
            .test()
            .expectNext(true)
            .verifyComplete()
        Assertions.assertTrue(semaphore.tryAcquire(1, TimeUnit.SECONDS))
        serviceDiscovery.getServices(namespace).collectList()
            .test()
            .expectNextMatches {
                Assertions.assertTrue(it.contains(serviceId))
                Assertions.assertTrue(it.contains(serviceId2))
                Assertions.assertEquals(2, it.size)
                true
            }
            .verifyComplete()
    }

    @Test
    fun instanceChangedEvent() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        val serviceId = MockIdGenerator.INSTANCE.generateAsString()
        val instance = createInstance(serviceId)
        val semaphore = Semaphore(0)
        val serviceDiscovery: ServiceDiscovery = ConsistencyRedisServiceDiscovery(
            delegate = delegate,
            serviceEventListenerContainer = serviceEventListenerContainer,
            instanceEventListenerContainer = instanceEventListenerContainer,
            hookOnResetInstanceCache = {
                if (it.namespacedServiceId.namespace == namespace) {
                    semaphore.release()
                }
            }
        )
        serviceDiscovery.getInstances(namespace, serviceId)
            .test()
            .expectNextCount(0)
            .verifyComplete()
        serviceRegistry.register(namespace, instance)
            .test()
            .expectNext(true)
            .verifyComplete()
        Assertions.assertTrue(semaphore.tryAcquire(1, TimeUnit.SECONDS))
        serviceDiscovery.getInstances(namespace, serviceId).collectList()
            .test()
            .expectNextMatches { it.contains(instance) }
            .verifyComplete()
        StepVerifier.create(serviceRegistry.deregister(namespace, instance))
            .expectNext(true)
            .verifyComplete()
        Assertions.assertTrue(semaphore.tryAcquire(1, TimeUnit.SECONDS))
        serviceDiscovery.getInstances(namespace, serviceId)
            .test()
            .expectNextCount(0)
            .verifyComplete()
        // wait for ttl
        TimeUnit.MILLISECONDS.sleep(registryProperties.instanceTtl.plusSeconds(1).toMillis())
        serviceDiscovery.getInstances(namespace, serviceId).collectList()
            .test()
            .expectNextMatches { it.isEmpty() }
            .verifyComplete()

        serviceRegistry.renew(namespace, instance)
            .test()
            .expectNext(true)
            .verifyComplete()

        Assertions.assertTrue(semaphore.tryAcquire(1, TimeUnit.SECONDS))

        serviceRegistry.setMetadata(namespace, instance.serviceId, instance.instanceId, mapOf("key" to "value"))
            .test()
            .expectNext(true)
            .verifyComplete()

        Assertions.assertTrue(semaphore.tryAcquire(1, TimeUnit.SECONDS))

        serviceRegistry.deregister(namespace, instance)
            .test()
            .expectNext(true)
            .verifyComplete()

        Assertions.assertTrue(semaphore.tryAcquire(1, TimeUnit.SECONDS))
    }

}
