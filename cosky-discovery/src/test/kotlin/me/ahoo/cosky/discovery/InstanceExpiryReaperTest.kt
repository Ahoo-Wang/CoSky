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
import me.ahoo.cosky.discovery.ServiceInstance.Companion.withIsEphemeral
import me.ahoo.cosky.discovery.TestServiceInstance.createInstance
import me.ahoo.cosky.discovery.redis.RedisInstanceEventListenerContainer
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry
import me.ahoo.cosky.test.AbstractReactiveRedisTest
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.kotlin.test.test
import java.time.Duration

class InstanceExpiryReaperTest : AbstractReactiveRedisTest() {
    private lateinit var listenerContainer: ReactiveRedisMessageListenerContainer
    private lateinit var serviceRegistry: RedisServiceRegistry
    private val namespace = MockIdGenerator.INSTANCE.generateAsString()

    override fun afterInitializedRedisClient() {
        listenerContainer = ReactiveRedisMessageListenerContainer(connectionFactory)
        serviceRegistry = RedisServiceRegistry(RegistryProperties(Duration.ofSeconds(1)), redisTemplate)
    }

    override fun beforeDestroyRedisClient() {
        listenerContainer.destroy()
    }

    private fun indexedMembers(): List<String> {
        return redisTemplate.opsForZSet()
            .range(
                DiscoveryKeyGenerator.getInstanceExpiryKey(namespace),
                org.springframework.data.domain.Range.unbounded()
            )
            .collectList()
            .block()!!
    }

    private fun isIndexed(instance: ServiceInstance): Boolean {
        return redisTemplate.opsForSet()
            .isMember(DiscoveryKeyGenerator.getInstanceIdxKey(namespace, instance.serviceId), instance.instanceId)
            .block()!!
    }

    @Test
    fun reapExpiredInstanceAndPublishExpired() {
        val instance = createInstance(MockIdGenerator.INSTANCE.generateAsString())
        val events = RedisInstanceEventListenerContainer(listenerContainer)
            .listen(NamespacedServiceId(namespace, instance.serviceId))
            .block()!!
            .filter { it.event == InstanceChangedEvent.Event.EXPIRED }
            .next()
            .cache()
        events.subscribe()
        serviceRegistry.register(namespace, instance).test().expectNext(true).verifyComplete()
        indexedMembers().size.assert().isEqualTo(1)

        // ttlAt has a resolution of one second
        Thread.sleep(2500)
        serviceRegistry.reapExpiredInstances(namespace).test().expectNext(1L).verifyComplete()

        isIndexed(instance).assert().isFalse()
        indexedMembers().assert().isEmpty()
        events.test().expectNextMatches { it.instance.instanceId == instance.instanceId }.verifyComplete()
    }

    /**
     * The index is only a candidate list: an instance whose key is still alive, e.g. renewed by a client that does
     * not maintain the index, is re-scored instead of reaped.
     */
    @Test
    fun liveInstanceIsRescoredInsteadOfReaped() {
        val instance = createInstance(MockIdGenerator.INSTANCE.generateAsString())
        RedisServiceRegistry(RegistryProperties(Duration.ofMinutes(1)), redisTemplate)
            .register(namespace, instance).test().expectNext(true).verifyComplete()
        val member = indexedMembers().single()
        redisTemplate.opsForZSet().add(DiscoveryKeyGenerator.getInstanceExpiryKey(namespace), member, 0.0).block()

        serviceRegistry.reapExpiredInstances(namespace).test().expectNext(0L).verifyComplete()

        isIndexed(instance).assert().isTrue()
        val score = redisTemplate.opsForZSet().score(
            DiscoveryKeyGenerator.getInstanceExpiryKey(namespace),
            member
        ).block()!!
        (score > System.currentTimeMillis() / 1000).assert().isTrue()
    }

    @Test
    fun fixedAndDeregisteredInstancesLeaveTheIndex() {
        val instance = createInstance(MockIdGenerator.INSTANCE.generateAsString())
        serviceRegistry.register(namespace, instance).test().expectNext(true).verifyComplete()
        indexedMembers().size.assert().isEqualTo(1)

        serviceRegistry.register(namespace, instance.withIsEphemeral(false)).test().expectNext(true).verifyComplete()
        indexedMembers().assert().isEmpty()

        serviceRegistry.register(namespace, instance).test().expectNext(true).verifyComplete()
        serviceRegistry.deregister(namespace, instance).test().expectNext(true).verifyComplete()
        indexedMembers().assert().isEmpty()
    }

    @Test
    fun renewRefreshesTheScore() {
        val registry = RedisServiceRegistry(RegistryProperties(Duration.ofMinutes(1)), redisTemplate)
        val instance = createInstance(MockIdGenerator.INSTANCE.generateAsString())
        registry.register(namespace, instance).test().expectNext(true).verifyComplete()
        val member = indexedMembers().single()
        redisTemplate.opsForZSet().add(DiscoveryKeyGenerator.getInstanceExpiryKey(namespace), member, 0.0).block()

        registry.renew(namespace, instance).test().expectNext(true).verifyComplete()

        val score = redisTemplate.opsForZSet().score(
            DiscoveryKeyGenerator.getInstanceExpiryKey(namespace),
            member
        ).block()!!
        (score > System.currentTimeMillis() / 1000).assert().isTrue()
    }
}
