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

import io.github.oshai.kotlinlogging.KotlinLogging
import me.ahoo.cosky.discovery.NamespacedInstanceId
import me.ahoo.cosky.discovery.RegistryProperties
import me.ahoo.cosky.discovery.ServiceInstance
import me.ahoo.cosky.discovery.ServiceInstanceCodec.encodeMetadata
import me.ahoo.cosky.discovery.ServiceInstanceCodec.encodeMetadataKey
import me.ahoo.cosky.discovery.ServiceRegistry
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.util.concurrent.ConcurrentHashMap

/**
 * Redis Service Registry.
 *
 * @author ahoo wang
 */
class RedisServiceRegistry(
    private val registryProperties: RegistryProperties,
    private val redisTemplate: ReactiveStringRedisTemplate
) : ServiceRegistry {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    override val registeredEphemeralInstances: ConcurrentHashMap<NamespacedInstanceId, ServiceInstance> =
        ConcurrentHashMap()

    private fun registerInternal(namespace: String, serviceInstance: ServiceInstance): Mono<Boolean> {
        val argVCapacity = 6 + serviceInstance.metadata.size * 2

        /**
         * ARGV
         */
        val values = buildList(argVCapacity) {
            add(if (serviceInstance.isEphemeral) registryProperties.instanceTtl.seconds.toString() else "-1")
            add(serviceInstance.serviceId)
            add(serviceInstance.instanceId)
            add(serviceInstance.schema)
            add(serviceInstance.host)
            add(serviceInstance.port.toString())
            add(serviceInstance.weight.toString())
            encodeMetadata(this, serviceInstance.metadata)
        }
        return redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_REGISTRY_REGISTER,
            listOf(namespace),
            values,
        ).next()
    }

    override fun setService(namespace: String, serviceId: String): Mono<Boolean> {
        require(namespace.isNotBlank()) { "namespace must not be blank!" }
        require(serviceId.isNotBlank()) { "serviceId must not be blank!" }
        log.info {
            "Set Service - serviceId:[$serviceId]  @ namespace:[$namespace]."
        }
        return redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_REGISTRY_SET_SERVICE,
            listOf(namespace),
            listOf(serviceId),
        ).next()
    }

    override fun removeService(namespace: String, serviceId: String): Mono<Boolean> {
        require(namespace.isNotBlank()) { "namespace must not be blank!" }
        require(serviceId.isNotBlank()) { "serviceId must not be blank!" }
        log.warn {
            "Remove Service - serviceId:[$serviceId]  @ namespace:[$namespace]."
        }
        return redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_REGISTRY_REMOVE_SERVICE,
            listOf(namespace),
            listOf(serviceId),
        ).next()
    }

    override fun register(namespace: String, serviceInstance: ServiceInstance): Mono<Boolean> {
        require(namespace.isNotBlank()) { "namespace must not be blank!" }
        log.info {
            "Register - instanceId:[${serviceInstance.instanceId}]  @ namespace:[$namespace]."
        }
        return registerInternal(namespace, serviceInstance).doOnSubscribe {
            addEphemeralInstance(namespace, serviceInstance)
        }
    }

    private fun addEphemeralInstance(namespace: String, serviceInstance: ServiceInstance) {
        if (!serviceInstance.isEphemeral) {
            return
        }
        registeredEphemeralInstances[NamespacedInstanceId(namespace, serviceInstance.instanceId)] =
            serviceInstance
    }

    private fun removeEphemeralInstance(namespace: String, instanceId: String) {
        registeredEphemeralInstances.remove(NamespacedInstanceId(namespace, instanceId))
    }

    private fun removeEphemeralInstance(namespace: String, serviceInstance: ServiceInstance) {
        if (!serviceInstance.isEphemeral) {
            return
        }
        registeredEphemeralInstances.remove(NamespacedInstanceId(namespace, serviceInstance.instanceId))
    }

    override fun setMetadata(
        namespace: String,
        serviceId: String,
        instanceId: String,
        key: String,
        value: String
    ): Mono<Boolean> {
        val values = listOf(instanceId, encodeMetadataKey(key), value)
        return setMetadataInternal(namespace, instanceId, values)
    }

    override fun setMetadata(
        namespace: String,
        serviceId: String,
        instanceId: String,
        metadata: Map<String, String>
    ): Mono<Boolean> {
        val argVCapacity = 1 + metadata.size * 2
        val values = buildList(argVCapacity) {
            add(instanceId)
            encodeMetadata(this, metadata)
        }
        return setMetadataInternal(namespace, instanceId, values)
    }

    private fun setMetadataInternal(namespace: String, instanceId: String, args: List<String>): Mono<Boolean> {
        require(namespace.isNotBlank()) { "namespace must not be blank!" }
        require(instanceId.isNotBlank()) { "instanceId must not be blank!" }
        log.info {
            "Set Metadata - instanceId:[$instanceId] @ namespace:[$namespace]."
        }
        return redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_REGISTRY_SET_METADATA,
            listOf(namespace),
            args,
        )
            .next()
    }

    override fun renew(namespace: String, serviceInstance: ServiceInstance): Mono<Boolean> {
        require(namespace.isNotBlank()) { "namespace must not be blank!" }
        log.debug {
            "Renew - instanceId:[${serviceInstance.instanceId}] @ namespace:[$namespace]."
        }
        if (!serviceInstance.isEphemeral) {
            log.warn {
                "Renew - instanceId:[${serviceInstance.instanceId}] @ namespace:[$namespace] is not ephemeral, can not renew."
            }
            return false.toMono()
        }
        return redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_REGISTRY_RENEW,
            listOf(namespace),
            listOf(serviceInstance.instanceId, registryProperties.instanceTtl.seconds.toString()),
        )
            .flatMap { status ->
                if (status <= 0) {
                    log.warn {
                        "Renew - instanceId:[${serviceInstance.instanceId}] @ namespace:[$namespace] status is [$status],register again."
                    }
                    return@flatMap register(namespace, serviceInstance)
                }
                true.toMono()
            }
            .next()
    }

    override fun deregister(namespace: String, serviceId: String, instanceId: String): Mono<Boolean> {
        log.info {
            "Deregister - instanceId:[$instanceId] @ namespace:[$namespace]."
        }
        return deregisterInternal(namespace, serviceId, instanceId)
            .doOnSubscribe {
                removeEphemeralInstance(namespace, instanceId)
            }
    }

    override fun deregister(namespace: String, serviceInstance: ServiceInstance): Mono<Boolean> {
        log.info {
            "Deregister - instanceId:[${serviceInstance.instanceId}] @ namespace:[$namespace]."
        }
        return deregisterInternal(namespace, serviceInstance.serviceId, serviceInstance.instanceId)
            .doOnSubscribe {
                removeEphemeralInstance(namespace, serviceInstance)
            }
    }

    private fun deregisterInternal(namespace: String, serviceId: String, instanceId: String): Mono<Boolean> {
        require(namespace.isNotBlank()) { "namespace must not be blank!" }
        require(serviceId.isNotBlank()) { "serviceId must not be blank!" }
        require(instanceId.isNotBlank()) { "instanceId must not be blank!" }
        return redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_REGISTRY_DEREGISTER,
            listOf(namespace),
            listOf(serviceId, instanceId),
        ).next()
    }
}
