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

import me.ahoo.cosky.discovery.NamespacedInstanceId
import me.ahoo.cosky.discovery.RegistryProperties
import me.ahoo.cosky.discovery.ServiceInstance
import me.ahoo.cosky.discovery.ServiceInstanceCodec.encodeMetadata
import me.ahoo.cosky.discovery.ServiceRegistry
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Mono
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
        private val log = LoggerFactory.getLogger(RedisServiceRegistry::class.java)
    }

    override val registeredEphemeralInstances: ConcurrentHashMap<NamespacedInstanceId, ServiceInstance> =
        ConcurrentHashMap()

    private fun register0(namespace: String, serviceInstance: ServiceInstance): Mono<Boolean> {
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
            values
        ).next()
    }

    override fun setService(namespace: String, serviceId: String): Mono<Boolean> {
        require(namespace.isNotBlank()) { "namespace must not be blank!" }
        require(serviceId.isNotBlank()) { "serviceId must not be blank!" }
        if (log.isInfoEnabled) {
            log.info("setService - serviceId:[{}]  @ namespace:[{}].", serviceId, namespace)
        }
        return redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_REGISTRY_SET_SERVICE,
            listOf(namespace),
            listOf(serviceId)
        ).next()
    }

    override fun removeService(namespace: String, serviceId: String): Mono<Boolean> {
        require(namespace.isNotBlank()) { "namespace must not be blank!" }
        require(serviceId.isNotBlank()) { "serviceId must not be blank!" }
        if (log.isWarnEnabled) {
            log.warn("removeService - serviceId:[{}]  @ namespace:[{}].", serviceId, namespace)
        }
        return redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_REGISTRY_REMOVE_SERVICE,
            listOf(namespace),
            listOf(serviceId)
        ).next()
    }

    override fun register(namespace: String, serviceInstance: ServiceInstance): Mono<Boolean> {
        require(namespace.isNotBlank()) { "namespace must not be blank!" }
        if (log.isInfoEnabled) {
            log.info("register - instanceId:[{}]  @ namespace:[{}].", serviceInstance.instanceId, namespace)
        }
        addEphemeralInstance(namespace, serviceInstance)
        return register0(namespace, serviceInstance)
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
        val values = listOf(instanceId, key, value)
        return setMetadata0(namespace, instanceId, values)
    }

    override fun setMetadata(
        namespace: String,
        serviceId: String,
        instanceId: String,
        metadata: Map<String, String>
    ): Mono<Boolean> {
        val argVCapacity = 1 + metadata.size * 2
        val values = buildList(argVCapacity) {
            this[0] = instanceId
            encodeMetadata(this, metadata)
        }
        return setMetadata0(namespace, instanceId, values)
    }

    private fun setMetadata0(namespace: String, instanceId: String, args: List<String>): Mono<Boolean> {
        require(namespace.isNotBlank()) { "namespace must not be blank!" }
        require(instanceId.isNotBlank()) { "instanceId must not be blank!" }
        if (log.isInfoEnabled) {
            log.info("setMetadata - instanceId:[{}] @ namespace:[{}].", instanceId, namespace)
        }
        return redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_REGISTRY_SET_METADATA,
            listOf(namespace),
            args
        )
            .next()
    }

    override fun renew(namespace: String, serviceInstance: ServiceInstance): Mono<Boolean> {
        require(namespace.isNotBlank()) { "namespace must not be blank!" }
        if (log.isDebugEnabled) {
            log.debug("renew - instanceId:[{}] @ namespace:[{}].", serviceInstance.instanceId, namespace)
        }
        if (!serviceInstance.isEphemeral) {
            if (log.isWarnEnabled) {
                log.warn(
                    "renew - instanceId:[{}] @ namespace:[{}] is not ephemeral, can not renew.",
                    serviceInstance.instanceId,
                    namespace
                )
            }
            return Mono.just(java.lang.Boolean.FALSE)
        }
        return redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_REGISTRY_RENEW,
            listOf(namespace),
            listOf(serviceInstance.instanceId, registryProperties.instanceTtl.seconds.toString())
        )
            .flatMap { status ->
                if (status <= 0) {
                    if (log.isWarnEnabled) {
                        log.warn(
                            "renew - instanceId:[{}] @ namespace:[{}] status is [{}],register again.",
                            serviceInstance.instanceId,
                            namespace,
                            status
                        )
                    }
                    return@flatMap register(namespace, serviceInstance)
                }
                Mono.just(java.lang.Boolean.TRUE)
            }
            .next()
    }

    override fun deregister(namespace: String, serviceId: String, instanceId: String): Mono<Boolean> {
        if (log.isInfoEnabled) {
            log.info("deregister - instanceId:[{}] @ namespace:[{}].", instanceId, namespace)
        }
        removeEphemeralInstance(namespace, instanceId)
        return deregister0(namespace, serviceId, instanceId)
    }

    override fun deregister(namespace: String, serviceInstance: ServiceInstance): Mono<Boolean> {
        if (log.isInfoEnabled) {
            log.info("deregister - instanceId:[{}] @ namespace:[{}].", serviceInstance.instanceId, namespace)
        }
        removeEphemeralInstance(namespace, serviceInstance)
        return deregister0(namespace, serviceInstance.serviceId, serviceInstance.instanceId)
    }

    private fun deregister0(namespace: String, serviceId: String, instanceId: String): Mono<Boolean> {
        require(namespace.isNotBlank()) { "namespace must not be blank!" }
        require(serviceId.isNotBlank()) { "serviceId must not be blank!" }
        require(instanceId.isNotBlank()) { "instanceId must not be blank!" }
        return redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_REGISTRY_DEREGISTER,
            listOf(namespace),
            listOf(serviceId, instanceId)
        ).next()
    }
}
