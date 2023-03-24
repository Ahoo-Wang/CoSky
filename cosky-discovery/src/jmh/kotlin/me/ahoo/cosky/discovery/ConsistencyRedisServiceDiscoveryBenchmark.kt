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

import me.ahoo.cosky.discovery.TestServiceInstance.randomFixedInstance
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery
import me.ahoo.cosky.discovery.redis.RedisInstanceEventListenerContainer
import me.ahoo.cosky.discovery.redis.RedisServiceDiscovery
import me.ahoo.cosky.discovery.redis.RedisServiceEventListenerContainer
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry
import me.ahoo.cosky.test.AbstractReactiveRedisTest
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
open class ConsistencyRedisServiceDiscoveryBenchmark : AbstractReactiveRedisTest() {
    private lateinit var serviceDiscovery: ServiceDiscovery

    @Setup
    override fun afterPropertiesSet() {
        super.afterPropertiesSet()
        val registryProperties = RegistryProperties()
        val serviceRegistry = RedisServiceRegistry(registryProperties, redisTemplate)
        serviceRegistry.register(TestData.NAMESPACE, fixedInstance).block()
        val redisServiceDiscovery = RedisServiceDiscovery(redisTemplate)
        val serviceEventListenerContainer = RedisServiceEventListenerContainer(
            ReactiveRedisMessageListenerContainer(connectionFactory),
        )
        val serviceInstanceEventListenerContainer = RedisInstanceEventListenerContainer(
            ReactiveRedisMessageListenerContainer(connectionFactory),
        )
        serviceDiscovery = ConsistencyRedisServiceDiscovery(
            redisServiceDiscovery,
            serviceEventListenerContainer,
            serviceInstanceEventListenerContainer,
        )
    }

    override val enableShare: Boolean
        get() = true

    @TearDown
    override fun destroy() {
        super.destroy()
    }

    @Benchmark
    fun getServices(): List<String> = serviceDiscovery
        .getServices(TestData.NAMESPACE)
        .collectList()
        .block()!!

    @Benchmark
    fun getInstances(): List<ServiceInstance> = serviceDiscovery
        .getInstances(TestData.NAMESPACE, fixedInstance.serviceId)
        .collectList()
        .block()!!

    companion object {
        private val fixedInstance = randomFixedInstance()
    }
}
