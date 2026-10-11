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
package me.ahoo.cosky.discovery.spring.cloud.discovery

import me.ahoo.cosid.test.MockIdGenerator
import me.ahoo.cosky.discovery.Instance
import me.ahoo.cosky.discovery.RegistryProperties
import me.ahoo.cosky.discovery.ServiceInstance.Companion.asServiceInstance
import me.ahoo.cosky.discovery.redis.RedisServiceDiscovery
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry
import me.ahoo.cosky.test.AbstractReactiveRedisTest
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import reactor.kotlin.test.test
import java.util.concurrent.ThreadLocalRandom

internal class CoSkyDiscoveryClientTest : AbstractReactiveRedisTest() {
    lateinit var serviceRegistry: RedisServiceRegistry
    lateinit var serviceDiscovery: RedisServiceDiscovery
    lateinit var coSkyDiscoveryClient: CoSkyDiscoveryClient

    override fun afterInitializedRedisClient() {
        serviceRegistry = RedisServiceRegistry(RegistryProperties(), redisTemplate)
        serviceDiscovery = RedisServiceDiscovery(redisTemplate)
        coSkyDiscoveryClient = CoSkyDiscoveryClient(serviceDiscovery, CoSkyDiscoveryProperties(), NAMESPACE)
    }

    @Test
    fun description() {
        coSkyDiscoveryClient.description().assert().isEqualTo("CoSky Discovery Client")
    }

    @Test
    fun getInstances() {
        val instance = Instance.asInstance(
            MockIdGenerator.INSTANCE.generateAsString(),
            "http",
            "127.0.0.1",
            ThreadLocalRandom.current().nextInt(65535),
        ).asServiceInstance(metadata = mapOf("from" to "test"))
        serviceRegistry.register(NAMESPACE, instance)
            .test()
            .expectNext(true)
            .verifyComplete()
        val instances = coSkyDiscoveryClient.getInstances(instance.serviceId)
            .map { it.instanceId }
        instances.assert().contains(instance.instanceId)
    }

    @Test
    fun getServices() {
        coSkyDiscoveryClient.services.assert().isNotNull()
    }

    @Test
    fun getOrder() {
        coSkyDiscoveryClient.order.assert().isZero()
    }

    companion object {
        private const val NAMESPACE = "cosky-{discovery-client-test}"
    }
}
