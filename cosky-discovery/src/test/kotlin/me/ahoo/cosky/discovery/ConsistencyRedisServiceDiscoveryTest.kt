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

import io.mockk.every
import io.mockk.mockk
import me.ahoo.cosid.test.MockIdGenerator
import me.ahoo.cosky.discovery.ServiceInstance.Companion.withIsEphemeral
import me.ahoo.cosky.discovery.TestServiceInstance.createInstance
import me.ahoo.cosky.discovery.TestServiceInstance.registerRandomInstanceAndTestThenDeregister
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery
import me.ahoo.cosky.discovery.redis.RedisInstanceEventListenerContainer
import me.ahoo.cosky.discovery.redis.RedisServiceDiscovery
import me.ahoo.cosky.discovery.redis.RedisServiceEventListenerContainer
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry
import me.ahoo.cosky.test.AbstractReactiveRedisTest
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.test.test
import java.time.Duration

/**
 * @author ahoo wang
 */
class ConsistencyRedisServiceDiscoveryTest : AbstractReactiveRedisTest() {
    private lateinit var delegate: RedisServiceDiscovery
    private lateinit var serviceDiscovery: ConsistencyRedisServiceDiscovery
    private lateinit var serviceRegistry: RedisServiceRegistry
    private lateinit var registryProperties: RegistryProperties
    private lateinit var listenerContainer: ReactiveRedisMessageListenerContainer

    override fun afterInitializedRedisClient() {
        registryProperties = RegistryProperties(Duration.ofSeconds(5))
        serviceRegistry = RedisServiceRegistry(registryProperties, redisTemplate)
        delegate = RedisServiceDiscovery(redisTemplate)
        listenerContainer = ReactiveRedisMessageListenerContainer(connectionFactory)
        serviceDiscovery = ConsistencyRedisServiceDiscovery(
            delegate = delegate,
            serviceEventListenerContainer = RedisServiceEventListenerContainer(listenerContainer),
            instanceEventListenerContainer = RedisInstanceEventListenerContainer(listenerContainer),
        )
    }

    override fun beforeDestroyRedisClient() {
        serviceDiscovery.close()
        listenerContainer.destroy()
    }

    private fun <T : Any> await(
        source: () -> Mono<T>,
        timeout: Duration = Duration.ofSeconds(5),
        predicate: (T) -> Boolean
    ): T {
        return Mono.defer(source)
            .filter(predicate)
            .repeatWhenEmpty { it.delayElements(Duration.ofMillis(20)) }
            .block(timeout)!!
    }

    private fun List<ServiceInstance>.find(instance: ServiceInstance): ServiceInstance? {
        return firstOrNull { it.instanceId == instance.instanceId }
    }

    @Test
    fun getServices() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        registerRandomInstanceAndTestThenDeregister(namespace, serviceRegistry) { instance ->
            serviceDiscovery.getServices(namespace)
                .test()
                .expectNextMatches { it.contains(instance.serviceId) }
                .verifyComplete()
        }
    }

    @Test
    fun getInstances() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        registerRandomInstanceAndTestThenDeregister(namespace, serviceRegistry) { instance ->
            serviceDiscovery.getInstances(namespace, instance.serviceId)
                .test()
                .expectNextMatches { it.find(instance)?.metadata == instance.metadata }
                .verifyComplete()
        }
    }

    @Test
    fun getInstance() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        registerRandomInstanceAndTestThenDeregister(namespace, serviceRegistry) { instance ->
            serviceDiscovery.getInstance(namespace, instance.serviceId, instance.instanceId)
                .test()
                .expectNextMatches { it.instanceId == instance.instanceId }
                .verifyComplete()
        }
    }

    @Test
    fun unchangedSnapshotIsTheSameList() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        registerRandomInstanceAndTestThenDeregister(namespace, serviceRegistry) { instance ->
            serviceDiscovery.getInstances(namespace, instance.serviceId)
                .zipWith(serviceDiscovery.getInstances(namespace, instance.serviceId))
                .test()
                .expectNextMatches { it.t1 === it.t2 }
                .verifyComplete()
        }
    }

    @Test
    fun getInstanceFallsBackWhenCachedServiceMissesInstance() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        val serviceId = MockIdGenerator.INSTANCE.generateAsString()
        val cachedInstance = createInstance(serviceId).withIsEphemeral(false)
        val missingInstance = createInstance(serviceId).withIsEphemeral(false)
        val delegate = mockk<ServiceDiscovery>()
        every { delegate.getInstances(namespace, serviceId) } returns Mono.just(listOf(cachedInstance))
        every { delegate.getInstance(namespace, serviceId, missingInstance.instanceId) } returns Mono.just(missingInstance)
        val serviceDiscovery = ConsistencyRedisServiceDiscovery(
            delegate = delegate,
            serviceEventListenerContainer = mockk(),
            instanceEventListenerContainer = mockk { every { listen(any()) } returns Mono.just(Flux.never()) },
        )

        serviceDiscovery.getInstances(namespace, serviceId).test()
            .expectNext(listOf(cachedInstance))
            .verifyComplete()
        serviceDiscovery.getInstance(namespace, serviceId, missingInstance.instanceId).test()
            .expectNext(missingInstance)
            .verifyComplete()
        serviceDiscovery.close()
    }

    @Test
    fun servicesFollowRegistrations() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        val serviceId = MockIdGenerator.INSTANCE.generateAsString()
        serviceDiscovery.getServices(namespace)
            .test()
            .expectNext(emptyList())
            .verifyComplete()
        serviceRegistry.register(namespace, createInstance(serviceId))
            .test()
            .expectNext(true)
            .verifyComplete()
        await({ serviceDiscovery.getServices(namespace) }) { it == listOf(serviceId) }

        val serviceId2 = MockIdGenerator.INSTANCE.generateAsString()
        serviceRegistry.register(namespace, createInstance(serviceId2))
            .test()
            .expectNext(true)
            .verifyComplete()
        await({ serviceDiscovery.getServices(namespace) }) { it.toSet() == setOf(serviceId, serviceId2) }
    }

    @Test
    fun instancesFollowInstanceEvents() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        val serviceId = MockIdGenerator.INSTANCE.generateAsString()
        val instance = createInstance(serviceId)
        serviceDiscovery.getInstances(namespace, serviceId)
            .test()
            .expectNext(emptyList())
            .verifyComplete()

        // REGISTER
        serviceRegistry.register(namespace, instance).test().expectNext(true).verifyComplete()
        val registered = await({ serviceDiscovery.getInstances(namespace, serviceId) }) { it.find(instance) != null }
            .find(instance)!!

        // SET_METADATA
        serviceRegistry.setMetadata(namespace, serviceId, instance.instanceId, mapOf("key" to "value"))
            .test()
            .expectNext(true)
            .verifyComplete()
        await({ serviceDiscovery.getInstances(namespace, serviceId) }) {
            it.find(instance)?.metadata == mapOf("key" to "value")
        }

        // RENEW: published once the last published ttlAt is about to expire.
        Thread.sleep(registryProperties.instanceTtl.minusSeconds(3).toMillis())
        serviceRegistry.renew(namespace, instance).test().expectNext(true).verifyComplete()
        await({ serviceDiscovery.getInstances(namespace, serviceId) }) {
            it.find(instance)!!.ttlAt > registered.ttlAt
        }

        // DEREGISTER
        serviceRegistry.deregister(namespace, instance).test().expectNext(true).verifyComplete()
        await({ serviceDiscovery.getInstances(namespace, serviceId) }) { it.isEmpty() }
    }

    /**
     * A registration right after the first read must reach the cache: the read only starts once the instance
     * event subscription is active.
     */
    @Test
    fun registerRightAfterFirstReadIsNotLost() {
        repeat(20) {
            val namespace = MockIdGenerator.INSTANCE.generateAsString()
            val instance = createInstance(MockIdGenerator.INSTANCE.generateAsString())
            serviceDiscovery.getInstances(namespace, instance.serviceId).subscribe()
            serviceRegistry.register(namespace, instance).test().expectNext(true).verifyComplete()
            await<List<ServiceInstance>>({ serviceDiscovery.getInstances(namespace, instance.serviceId) }) {
                it.find(instance) != null
            }
        }
    }

    @Test
    fun expiredInstanceIsFilteredOut() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        val instance = createInstance(MockIdGenerator.INSTANCE.generateAsString())
        serviceRegistry.register(namespace, instance).test().expectNext(true).verifyComplete()
        serviceDiscovery.getInstances(namespace, instance.serviceId)
            .test()
            .expectNextMatches { it.find(instance) != null }
            .verifyComplete()

        // ttlAt has a resolution of one second.
        val timeout = registryProperties.instanceTtl.plusSeconds(3)
        await({ serviceDiscovery.getInstances(namespace, instance.serviceId) }, timeout) { it.isEmpty() }
            .assert().isEmpty()
    }
}
