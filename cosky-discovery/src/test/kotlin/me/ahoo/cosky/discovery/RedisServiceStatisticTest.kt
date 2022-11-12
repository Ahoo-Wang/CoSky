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
import me.ahoo.cosky.discovery.TestServiceInstance.randomInstance
import me.ahoo.cosky.discovery.redis.RedisInstanceEventListenerContainer
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry
import me.ahoo.cosky.discovery.redis.RedisServiceStatistic
import me.ahoo.cosky.test.AbstractReactiveRedisTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.kotlin.test.test

/**
 * @author ahoo wang
 */
class RedisServiceStatisticTest : AbstractReactiveRedisTest() {
    private lateinit var redisServiceStatistic: RedisServiceStatistic
    private lateinit var serviceRegistry: RedisServiceRegistry
    private lateinit var instanceEventListenerContainer: InstanceEventListenerContainer
    override fun afterInitializedRedisClient() {
        val registryProperties = RegistryProperties()
        serviceRegistry = RedisServiceRegistry(registryProperties, redisTemplate)
        instanceEventListenerContainer = RedisInstanceEventListenerContainer(
            ReactiveRedisMessageListenerContainer(connectionFactory)
        )
        redisServiceStatistic = RedisServiceStatistic(redisTemplate, instanceEventListenerContainer)
    }

    @Test
    fun statService() {
        val getServiceStatInstance = randomInstance()
        serviceRegistry.register(namespace, getServiceStatInstance)
            .test()
            .expectNext(true)
            .verifyComplete()
        redisServiceStatistic.statService(namespace)
            .test()
            .verifyComplete()
        redisServiceStatistic.getServiceStats(namespace).collectList()
            .test()
            .expectNextMatches {
                assertThat(it.size, equalTo(1))
                val (_, instanceCount) = it[0]
                assertThat(instanceCount, equalTo(1))
                true
            }
    }

    @Test
    fun statServiceWhenServiceIdIsNull() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        redisServiceStatistic.statService(namespace)
            .test()
            .verifyComplete()
    }

    @Test
    fun countService() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        redisServiceStatistic.countService(namespace)
            .test()
            .expectNext(0)
            .verifyComplete()
    }

    @Test
    fun getInstanceCount() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        redisServiceStatistic.getInstanceCount(namespace)
            .test()
            .expectNext(0)
            .verifyComplete()
    }

    companion object {
        private val namespace = MockIdGenerator.INSTANCE.generateAsString()
    }
}
