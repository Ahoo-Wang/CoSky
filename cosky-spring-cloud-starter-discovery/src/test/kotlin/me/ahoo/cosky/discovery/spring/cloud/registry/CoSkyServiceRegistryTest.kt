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
package me.ahoo.cosky.discovery.spring.cloud.registry

import me.ahoo.cosid.test.MockIdGenerator
import me.ahoo.cosky.discovery.RegistryProperties
import me.ahoo.cosky.discovery.RenewInstanceService
import me.ahoo.cosky.discovery.RenewProperties
import me.ahoo.cosky.discovery.ServiceDiscovery
import me.ahoo.cosky.discovery.redis.RedisServiceDiscovery
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry
import me.ahoo.cosky.test.AbstractReactiveRedisTest
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import reactor.kotlin.test.test
import java.time.Duration

internal class CoSkyServiceRegistryTest : AbstractReactiveRedisTest() {
    private lateinit var serviceRegistry: RedisServiceRegistry
    private lateinit var serviceDiscovery: ServiceDiscovery
    private lateinit var renewInstanceService: RenewInstanceService
    private lateinit var coSkyServiceRegistry: CoSkyServiceRegistry
    override fun afterInitializedRedisClient() {
        val registryProperties = RegistryProperties(Duration.ofSeconds(10))
        serviceRegistry = RedisServiceRegistry(registryProperties, redisTemplate)
        serviceDiscovery = RedisServiceDiscovery(redisTemplate)
        renewInstanceService = RenewInstanceService(RenewProperties(), serviceRegistry)
        val coSkyRegistryProperties = CoSkyRegistryProperties().apply {
            serviceId = "app"
            host = "app-1"
        }
        coSkyServiceRegistry =
            CoSkyServiceRegistry(serviceRegistry, renewInstanceService, coSkyRegistryProperties, NAMESPACE)
    }

    @Test
    fun register() {
        val serviceId = "register-app-${MockIdGenerator.INSTANCE.generateAsString()}"
        val registration = CoSkyRegistration(serviceId = serviceId, scheme = "http", host = "app-1", port = 8080)
        coSkyServiceRegistry.register(registration)
        serviceDiscovery.getInstances(NAMESPACE, serviceId)
            .test()
            .expectNextMatches { it.size == 1 }
            .verifyComplete()
    }

    @Test
    fun deregister() {
        val serviceId = "deregister-app-${MockIdGenerator.INSTANCE.generateAsString()}"
        val registration = CoSkyRegistration(serviceId = serviceId, scheme = "http", host = "app-1", port = 8080)
        coSkyServiceRegistry.register(registration)
        coSkyServiceRegistry.deregister(registration)
        serviceDiscovery.getInstances(NAMESPACE, serviceId)
            .test()
            .expectNext(emptyList())
            .verifyComplete()
    }

    @Test
    fun close() {
        coSkyServiceRegistry.close()
    }

    @Test
    fun setStatus() {
        val serviceId = "setStatus-app-${MockIdGenerator.INSTANCE.generateAsString()}"
        val registration = CoSkyRegistration(serviceId = serviceId, scheme = "http", host = "app-1", port = 8080)
        coSkyServiceRegistry.register(registration)
        val expectedStatus = "UP"
        coSkyServiceRegistry.setStatus(registration, expectedStatus)
        val actualStatus = coSkyServiceRegistry.getStatus<String>(registration)
        expectedStatus.assert().isEqualTo(actualStatus)
    }

    companion object {
        private const val NAMESPACE = "cosky-{service-registry-test}"
    }
}
