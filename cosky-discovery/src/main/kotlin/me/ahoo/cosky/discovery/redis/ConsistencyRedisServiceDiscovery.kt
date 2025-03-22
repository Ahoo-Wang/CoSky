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
import me.ahoo.cosky.discovery.InstanceChangedEvent
import me.ahoo.cosky.discovery.InstanceEventListenerContainer
import me.ahoo.cosky.discovery.NamespacedServiceId
import me.ahoo.cosky.discovery.ServiceDiscovery
import me.ahoo.cosky.discovery.ServiceEventListenerContainer
import me.ahoo.cosky.discovery.ServiceInstance
import me.ahoo.cosky.discovery.ServiceInstance.Companion.withTtlAt
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.function.Function

private object NoOpHookOnResetInstanceCache : (InstanceChangedEvent) -> Unit {
    override fun invoke(p1: InstanceChangedEvent) = Unit
}

private object NoOpHookOnResetServiceCache : (String) -> Unit {
    override fun invoke(p1: String) = Unit
}

/**
 * Consistency Redis Service Discovery.
 *
 * @author ahoo wang
 */
class ConsistencyRedisServiceDiscovery(
    private val delegate: ServiceDiscovery,
    private val serviceEventListenerContainer: ServiceEventListenerContainer,
    private val instanceEventListenerContainer: InstanceEventListenerContainer,
    private val hookOnResetInstanceCache: (InstanceChangedEvent) -> Unit = NoOpHookOnResetInstanceCache,
    private val hookOnResetServiceCache: (String) -> Unit = NoOpHookOnResetServiceCache
) : ServiceDiscovery {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val serviceMapInstances =
        ConcurrentHashMap<NamespacedServiceId, Mono<CopyOnWriteArraySet<ServiceInstance>>>()

    private val namespaceMapServices: ConcurrentHashMap<String, Flux<String>> = ConcurrentHashMap()

    override fun getServices(namespace: String): Flux<String> {
        require(namespace.isNotBlank()) { "namespace must not be blank!" }
        return namespaceMapServices.computeIfAbsent(namespace) {
            @Suppress("CallingSubscribeInNonBlockingScope")
            serviceEventListenerContainer.receive(namespace)
                .doOnNext {
                    onServiceChanged(it)
                }.doFinally {
                    log.info {
                        "Listen topic[$namespace] finally - [$it]."
                    }
                    namespaceMapServices.remove(namespace)
                }
                .subscribe()
            delegate.getServices(namespace).cache()
        }
    }

    private fun onServiceChanged(namespace: String) {
        log.debug {
            "onServiceChanged:$namespace"
        }
        @Suppress("ReactiveStreamsUnusedPublisher")
        namespaceMapServices[namespace] = delegate.getServices(namespace).cache()
        hookOnResetServiceCache(namespace)
    }

    override fun getInstances(namespace: String, serviceId: String): Flux<ServiceInstance> {
        require(namespace.isNotBlank()) { "namespace must not be blank!" }
        require(serviceId.isNotBlank()) { "serviceId must not be blank!" }
        return serviceMapInstances.computeIfAbsent(
            NamespacedServiceId(namespace, serviceId),
        ) { svcId ->
            @Suppress("CallingSubscribeInNonBlockingScope")
            instanceEventListenerContainer.receive(svcId)
                .doOnNext {
                    onInstanceChanged(it)
                }
                .doFinally {
                    log.info {
                        "Listen topic[$svcId] finally - [$it]."
                    }
                    serviceMapInstances.remove(svcId)
                }.subscribe()
            delegate.getInstances(namespace, serviceId)
                .collectList()
                .map {
                    CopyOnWriteArraySet(it)
                }
                .cache()
        }
            .flatMapIterable { it }
            .filter { instance: ServiceInstance -> !instance.isExpired }
    }

    private fun getInstanceInternal(namespace: String, serviceId: String, instanceId: String): Mono<ServiceInstance> {
        require(namespace.isNotBlank()) { "namespace must not be blank!" }
        require(serviceId.isNotBlank()) { "serviceId must not be blank!" }
        require(instanceId.isNotBlank()) { "instanceId must not be blank!" }
        val namespacedServiceId = NamespacedServiceId(namespace, serviceId)
        val instancesMono = serviceMapInstances[namespacedServiceId]
            ?: return delegate.getInstance(namespace, serviceId, instanceId)

        return instancesMono
            .flatMapIterable(Function.identity())
            .switchIfEmpty(delegate.getInstance(namespace, serviceId, instanceId))
            .filter { it.instanceId == instanceId }
            .next()
    }

    override fun getInstance(namespace: String, serviceId: String, instanceId: String): Mono<ServiceInstance> {
        return getInstanceInternal(namespace, serviceId, instanceId)
    }

    override fun getInstanceTtl(namespace: String, serviceId: String, instanceId: String): Mono<Long> {
        return getInstanceInternal(namespace, serviceId, instanceId)
            .map(ServiceInstance::ttlAt)
    }

    private fun onInstanceChanged(instanceChangedEvent: InstanceChangedEvent) {
        log.debug {
            "onInstanceChanged - instance:[${instanceChangedEvent.instance}] - message:[${instanceChangedEvent.event}]"
        }
        val namespacedServiceId = instanceChangedEvent.namespacedServiceId
        val instance = instanceChangedEvent.instance
        val instanceId = instance.instanceId
        val namespace = namespacedServiceId.namespace
        val serviceId = namespacedServiceId.serviceId
        val instancesMono = serviceMapInstances[namespacedServiceId]
        if (instancesMono == null) {
            log.debug {
                "onInstanceChanged - instance:[$instance] - event:[${instanceChangedEvent.event}] instancesMono is null."
            }
            return
        }
        instancesMono.flatMap { cachedInstances: CopyOnWriteArraySet<ServiceInstance> ->
            val cachedInstance =
                cachedInstances.firstOrNull { it.instanceId == instanceId } ?: ServiceInstance.NOT_FOUND
            if (ServiceInstance.NOT_FOUND == cachedInstance) {
                if (InstanceChangedEvent.Event.REGISTER != instanceChangedEvent.event &&
                    InstanceChangedEvent.Event.RENEW != instanceChangedEvent.event
                ) {
                    log.debug {
                        "onInstanceChanged - instance:[$instance] - event:[${instanceChangedEvent.event}] not found cached Instance."
                    }
                    return@flatMap Mono.empty<Any>()
                }
                return@flatMap delegate.getInstance(namespace, serviceId, instanceId)
                    .doOnNext { serviceInstance: ServiceInstance ->
                        cachedInstances.add(serviceInstance)
                    }
            }
            when (instanceChangedEvent.event) {
                InstanceChangedEvent.Event.REGISTER -> {
                    return@flatMap delegate
                        .getInstance(namespace, serviceId, instanceId)
                        .doOnNext { registeredInstance: ServiceInstance ->
                            cachedInstances.remove(cachedInstance)
                            cachedInstances.add(registeredInstance)
                        }
                }

                InstanceChangedEvent.Event.RENEW -> {
                    return@flatMap delegate
                        .getInstanceTtl(namespace, serviceId, instanceId)
                        .doOnNext { ttlAt ->
                            // TODO remove first
                            cachedInstances.remove(cachedInstance)
                            cachedInstances.add(cachedInstance.withTtlAt(ttlAt = ttlAt))
                        }
                }

                InstanceChangedEvent.Event.SET_METADATA -> {
                    return@flatMap delegate
                        .getInstance(namespace, serviceId, instanceId)
                        .doOnNext { updatedInstance ->
                            cachedInstances.remove(cachedInstance)
                            cachedInstances.add(updatedInstance)
                        }
                }

                InstanceChangedEvent.Event.DEREGISTER, InstanceChangedEvent.Event.EXPIRED -> {
                    cachedInstances.remove(cachedInstance)
                    return@flatMap Mono.empty<Any>()
                }

                else -> return@flatMap IllegalStateException(
                    "Unexpected value: " + instanceChangedEvent.event
                ).toMono<Any>()
            }
        }.doFinally {
            hookOnResetInstanceCache(instanceChangedEvent)
        }.subscribe()
    }
}
