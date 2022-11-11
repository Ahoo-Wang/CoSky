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

import me.ahoo.cosky.core.NamespaceService
import me.ahoo.cosky.core.NamespacedContext
import me.ahoo.cosky.core.redis.RedisNamespaceService
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry
import me.ahoo.cosky.discovery.redis.RedisServiceTopology
import me.ahoo.cosky.test.AbstractReactiveRedisTest
import org.junit.jupiter.api.Test
import reactor.kotlin.test.test
import java.util.concurrent.ThreadLocalRandom

/**
 * @author ahoo wang
 */
class ServiceTopologyTest : AbstractReactiveRedisTest() {
    private lateinit var serviceTopology: ServiceTopology
    private lateinit var serviceRegistry: ServiceRegistry
    private lateinit var redisNamespaceService: NamespaceService
    override fun afterInitializedRedisClient() {
        serviceRegistry = RedisServiceRegistry(RegistryProperties(), redisTemplate)
        serviceTopology = RedisServiceTopology(redisTemplate)
        redisNamespaceService = RedisNamespaceService(redisTemplate)
    }

    @Test
    fun buildTopologyData() {
        redisNamespaceService.setNamespace(namespace)
            .test()
            .expectNextCount(1)
            .verifyComplete()
        NamespacedContext.namespace = namespace
        val serviceSize = 50
        for (i in 0 until serviceSize) {
            ServiceInstanceContext.serviceInstance = TestServiceInstance.createInstance("service-$i")
            for (j in 0..4) {
                val depServiceId = ThreadLocalRandom.current().nextInt(0, serviceSize)
                if (depServiceId == i) {
                    continue
                }
                val serviceId = "service-$depServiceId"
                serviceRegistry.setService(namespace, serviceId)
                    .test()
                    .expectNextCount(1)
                    .verifyComplete()
                serviceTopology.addTopology(namespace, serviceId)
                    .test()
                    .verifyComplete()
            }
        }
    }

    companion object {
        private const val namespace = "topology"
    }
}
