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
package me.ahoo.cosky.discovery.redis

import me.ahoo.cosky.discovery.DiscoveryKeyGenerator.getServiceIdxKey
import me.ahoo.cosky.discovery.ServiceDiscovery
import me.ahoo.cosky.discovery.ServiceInstance
import me.ahoo.cosky.discovery.ServiceInstanceCodec.decode
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Redis Service Discovery.
 *
 * @author ahoo wang
 */
class RedisServiceDiscovery(
    private val redisTemplate: ReactiveStringRedisTemplate
) : ServiceDiscovery {

    override fun getInstances(
        namespace: String,
        serviceId: String
    ): Flux<ServiceInstance> {
        require(namespace.isNotBlank()) { "namespace is blank!" }
        require(serviceId.isNotBlank()) { "serviceId is blank!" }
        return redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_REGISTRY_GET_INSTANCES,
            listOf(namespace),
            listOf(serviceId)
        )
            .flatMapIterable { instanceGroups ->
                @Suppress("UNCHECKED_CAST")
                val groups = instanceGroups as List<List<String>>
                if (instanceGroups.isEmpty()) {
                    return@flatMapIterable emptyList<ServiceInstance>()
                }
                groups.map {
                    decode(it)
                }
            }
    }

    override fun getInstance(namespace: String, serviceId: String, instanceId: String): Mono<ServiceInstance> {
        require(namespace.isNotBlank()) { "namespace is blank!" }
        require(serviceId.isNotBlank()) { "serviceId is blank!" }
        require(instanceId.isNotBlank()) { "instanceId is blank!" }
        return redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_REGISTRY_GET_INSTANCE,
            listOf(namespace),
            listOf(serviceId, instanceId)
        )
            .map {
                @Suppress("UNCHECKED_CAST")
                it as List<String>
            }
            .mapNotNull<ServiceInstance> {
                if (it.isEmpty()) {
                    return@mapNotNull null
                }
                decode(it)
            }
            .next()
    }

    override fun getInstanceTtl(namespace: String, serviceId: String, instanceId: String): Mono<Long> {
        require(namespace.isNotBlank()) { "namespace is blank!" }
        require(serviceId.isNotBlank()) { "serviceId is blank!" }
        require(instanceId.isNotBlank()) { "instanceId is blank!" }
        return redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_REGISTRY_GET_INSTANCE_TTL,
            listOf(namespace),
            listOf(serviceId, instanceId)
        ).next()
    }

    override fun getServices(namespace: String): Flux<String> {
        require(namespace.isNotBlank()) { "namespace is blank!" }
        val serviceIdxKey = getServiceIdxKey(namespace)
        return redisTemplate
            .opsForSet()
            .members(serviceIdxKey)
    }
}
