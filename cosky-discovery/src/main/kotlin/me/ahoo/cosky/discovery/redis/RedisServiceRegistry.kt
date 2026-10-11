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
import me.ahoo.cosky.core.requireValidNamespace
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator.getInstanceExpiryKey
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator.getInstanceIdxKey
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator.getInstanceKey
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator.getServiceIdxKey
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator.getServiceStatKey
import me.ahoo.cosky.discovery.NamespacedInstanceId
import me.ahoo.cosky.discovery.RegistryProperties
import me.ahoo.cosky.discovery.ServiceInstance
import me.ahoo.cosky.discovery.ServiceInstance.Companion.withMetadata
import me.ahoo.cosky.discovery.ServiceInstanceCodec.encodeMetadata
import me.ahoo.cosky.discovery.ServiceInstanceCodec.encodeMetadataKey
import me.ahoo.cosky.discovery.ServiceRegistry
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
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
            listOf(
                getServiceIdxKey(namespace),
                getServiceStatKey(namespace),
                getInstanceIdxKey(namespace, serviceInstance.serviceId),
                getInstanceKey(namespace, serviceInstance.instanceId),
                getInstanceExpiryKey(namespace),
            ),
            values,
        ).next()
    }

    override fun setService(namespace: String, serviceId: String): Mono<Boolean> {
        requireValidNamespace(namespace)
        require(serviceId.isNotBlank()) { "serviceId must not be blank!" }
        log.info {
            "Set Service - serviceId:[$serviceId]  @ namespace:[$namespace]."
        }
        return redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_REGISTRY_SET_SERVICE,
            listOf(getServiceIdxKey(namespace), getServiceStatKey(namespace)),
            listOf(serviceId),
        ).next()
    }

    override fun removeService(namespace: String, serviceId: String): Mono<Boolean> {
        requireValidNamespace(namespace)
        require(serviceId.isNotBlank()) { "serviceId must not be blank!" }
        log.warn {
            "Remove Service - serviceId:[$serviceId]  @ namespace:[$namespace]."
        }
        return redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_REGISTRY_REMOVE_SERVICE,
            listOf(getServiceIdxKey(namespace), getServiceStatKey(namespace), getInstanceIdxKey(namespace, serviceId)),
            listOf(serviceId),
        ).next()
    }

    override fun register(namespace: String, serviceInstance: ServiceInstance): Mono<Boolean> {
        requireValidNamespace(namespace)
        log.info {
            "Register - instanceId:[${serviceInstance.instanceId}]  @ namespace:[$namespace]."
        }
        return registerInternal(namespace, serviceInstance).doOnNext { registered ->
            if (registered) {
                addEphemeralInstance(namespace, serviceInstance)
            }
        }
    }

    private fun addEphemeralInstance(namespace: String, serviceInstance: ServiceInstance) {
        val namespacedInstanceId = NamespacedInstanceId(namespace, serviceInstance.instanceId)
        if (!serviceInstance.isEphemeral) {
            registeredEphemeralInstances.remove(namespacedInstanceId)
            return
        }
        registeredEphemeralInstances[namespacedInstanceId] = serviceInstance
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
        val values = listOf(encodeMetadataKey(key), value)
        return setMetadataInternal(DiscoveryRedisScripts.SCRIPT_REGISTRY_PUT_METADATA, namespace, instanceId, values)
            .doOnNext { updated ->
                if (updated) {
                    updateEphemeralInstance(namespace, instanceId) { it.withMetadata(it.metadata + (key to value)) }
                }
            }
    }

    override fun setMetadata(
        namespace: String,
        serviceId: String,
        instanceId: String,
        metadata: Map<String, String>
    ): Mono<Boolean> {
        val values = encodeMetadata(ArrayList(metadata.size * 2), metadata)
        return setMetadataInternal(DiscoveryRedisScripts.SCRIPT_REGISTRY_SET_METADATA, namespace, instanceId, values)
            .doOnNext { updated ->
                if (updated) {
                    updateEphemeralInstance(namespace, instanceId) { it.withMetadata(metadata.toMap()) }
                }
            }
    }

    /**
     * Keeps the registered instance in line with Redis, so that registering it again after it expired restores its
     * current metadata.
     */
    private fun updateEphemeralInstance(
        namespace: String,
        instanceId: String,
        update: (ServiceInstance) -> ServiceInstance
    ) {
        registeredEphemeralInstances.computeIfPresent(NamespacedInstanceId(namespace, instanceId)) { _, instance ->
            update(instance)
        }
    }

    private fun setMetadataInternal(
        script: RedisScript<Boolean>,
        namespace: String,
        instanceId: String,
        args: List<String>
    ): Mono<Boolean> {
        requireValidNamespace(namespace)
        require(instanceId.isNotBlank()) { "instanceId must not be blank!" }
        log.info {
            "Set Metadata - instanceId:[$instanceId] @ namespace:[$namespace]."
        }
        return redisTemplate.execute(
            script,
            listOf(getInstanceKey(namespace, instanceId)),
            args,
        )
            .next()
    }

    override fun renew(namespace: String, serviceInstance: ServiceInstance): Mono<Boolean> {
        requireValidNamespace(namespace)
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
            listOf(getInstanceKey(namespace, serviceInstance.instanceId), getInstanceExpiryKey(namespace)),
            listOf(
                registryProperties.instanceTtl.seconds.toString(),
                serviceInstance.serviceId,
                serviceInstance.instanceId,
                if (registryProperties.renewEventCarriesTtlAt) "1" else "0",
            ),
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
            .doOnNext { deregistered ->
                if (deregistered) {
                    removeEphemeralInstance(namespace, instanceId)
                }
            }
    }

    override fun deregister(namespace: String, serviceInstance: ServiceInstance): Mono<Boolean> {
        log.info {
            "Deregister - instanceId:[${serviceInstance.instanceId}] @ namespace:[$namespace]."
        }
        return deregisterInternal(namespace, serviceInstance.serviceId, serviceInstance.instanceId)
            .doOnNext { deregistered ->
                if (deregistered) {
                    removeEphemeralInstance(namespace, serviceInstance)
                }
            }
    }

    private fun deregisterInternal(namespace: String, serviceId: String, instanceId: String): Mono<Boolean> {
        requireValidNamespace(namespace)
        require(serviceId.isNotBlank()) { "serviceId must not be blank!" }
        require(instanceId.isNotBlank()) { "instanceId must not be blank!" }
        return redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_REGISTRY_DEREGISTER,
            listOf(
                getInstanceIdxKey(namespace, serviceId),
                getInstanceKey(namespace, instanceId),
                getInstanceExpiryKey(namespace),
            ),
            listOf(instanceId, serviceId),
        ).next()
    }

    override fun reapExpiredInstances(namespace: String, limit: Int): Mono<Long> {
        requireValidNamespace(namespace)
        require(limit > 0) { "limit must be positive!" }
        return redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_REGISTRY_REAP,
            listOf(getInstanceExpiryKey(namespace)),
            listOf(namespace, limit.toString()),
        ).next()
    }
}
