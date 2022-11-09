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

import me.ahoo.cosky.discovery.DiscoveryKeyGenerator.getInstanceIdOfKey
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator.getInstanceKeyPatternOfService
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator.getNamespaceOfKey
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator.getServiceIdxKey
import me.ahoo.cosky.discovery.Instance
import me.ahoo.cosky.discovery.Instance.Companion.asInstance
import me.ahoo.cosky.discovery.ListenableServiceDiscovery
import me.ahoo.cosky.discovery.NamespacedServiceId
import me.ahoo.cosky.discovery.ServiceChangedEvent
import me.ahoo.cosky.discovery.ServiceChangedEvent.Companion.asServiceChangedEvent
import me.ahoo.cosky.discovery.ServiceDiscovery
import me.ahoo.cosky.discovery.ServiceInstance
import me.ahoo.cosky.discovery.ServiceInstanceContext
import me.ahoo.cosky.discovery.ServiceTopology
import me.ahoo.cosky.discovery.ServiceTopology.Companion.consumerName
import me.ahoo.cosky.discovery.ServiceTopology.Companion.getProducerName
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.ReactiveSubscription
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.core.publisher.BaseSubscriber
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.function.Consumer
import java.util.function.Function

private object NoOpHookOnResetInstanceCache : (ServiceChangedEvent) -> Unit {
    override fun invoke(p1: ServiceChangedEvent) = Unit
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
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val listenerContainer: ReactiveRedisMessageListenerContainer,
    private val hookOnResetInstanceCache: (ServiceChangedEvent) -> Unit = NoOpHookOnResetInstanceCache,
    private val hookOnResetServiceCache: (String) -> Unit = NoOpHookOnResetServiceCache
) : ListenableServiceDiscovery, ServiceTopology {
    companion object {
        private val log = LoggerFactory.getLogger(ConsistencyRedisServiceDiscovery::class.java)
    }

    private val serviceMapInstances: ConcurrentHashMap<NamespacedServiceId, Mono<CopyOnWriteArraySet<ServiceInstance>>> =
        ConcurrentHashMap()
    private val namespaceMapServices: ConcurrentHashMap<String, Flux<String>> = ConcurrentHashMap()

    override fun listen(topic: NamespacedServiceId): Flux<ServiceChangedEvent> {
        val instancePattern = getInstanceKeyPatternOfService(topic.namespace, topic.serviceId)
        return listenerContainer.receive(PatternTopic.of(instancePattern))
            .map {
                val namespace = getNamespaceOfKey(it.channel)
                val instanceId = getInstanceIdOfKey(namespace, it.channel)
                val instance: Instance = instanceId.asInstance()
                val serviceId = instance.serviceId
                val namespacedServiceId = NamespacedServiceId(namespace, serviceId)
                ServiceChangedEvent(namespacedServiceId, it.message.asServiceChangedEvent(), instance)
            }
            .doOnComplete {
                @Suppress("CallingSubscribeInNonBlockingScope")
                addTopology(topic.namespace, topic.serviceId).subscribe()
            }
    }

    override fun getServices(namespace: String): Flux<String> {
        require(namespace.isNotBlank()) { "namespace must not be blank!" }
        return namespaceMapServices.computeIfAbsent(namespace) { listenServiceAndGetCache(namespace) }
    }

    private fun listenServiceAndGetCache(namespace: String): Flux<String> {
        val serviceIdxKey = getServiceIdxKey(namespace)
        listenerContainer.receive(ChannelTopic.of(serviceIdxKey))
            .subscribe(ServiceChangedSubscriber(this))
        return delegate.getServices(namespace).cache()
    }

    private fun onServiceChanged(message: ReactiveSubscription.Message<String, String>) {
        if (log.isInfoEnabled) {
            log.info("onServiceChanged - channel:[{}] - message:[{}]", message.channel, message.message)
        }
        val namespace = getNamespaceOfKey(message.channel)
        namespaceMapServices[namespace] = delegate.getServices(namespace).cache()
        hookOnResetServiceCache(namespace)
    }

    override fun getInstances(namespace: String, serviceId: String): Flux<ServiceInstance> {
        require(namespace.isNotBlank()) { "namespace must not be blank!" }
        require(serviceId.isNotBlank()) { "serviceId must not be blank!" }
        return serviceMapInstances.computeIfAbsent(
            NamespacedServiceId(namespace, serviceId)
        ) { svcId: NamespacedServiceId ->
            listen(svcId).subscribe(InstanceChangedEventSubscriber(this))
            delegate.getInstances(namespace, serviceId)
                .collectList()
                .map {
                    CopyOnWriteArraySet(it)
                }
                .cache()
        }
            .flatMapIterable(Function.identity())
            .filter { instance: ServiceInstance -> !instance.isExpired }
    }

    fun getInstance0(namespace: String, serviceId: String, instanceId: String): Mono<ServiceInstance> {
        require(namespace.isNotBlank()) { "namespace must not be blank!" }
        require(serviceId.isNotBlank()) { "serviceId must not be blank!" }
        require(instanceId.isNotBlank()) { "instanceId must not be blank!" }
        val namespacedServiceId = NamespacedServiceId(namespace, serviceId)
        val instancesMono = serviceMapInstances[namespacedServiceId]
        return if (instancesMono == null) {
            delegate.getInstance(namespace, serviceId, instanceId)
        } else instancesMono
            .flatMapIterable(Function.identity())
            .switchIfEmpty(delegate.getInstance(namespace, serviceId, instanceId))
            .filter { it.instanceId == instanceId }
            .next()
    }

    override fun getInstance(namespace: String, serviceId: String, instanceId: String): Mono<ServiceInstance> {
        return getInstance0(namespace, serviceId, instanceId)
    }

    override fun getInstanceTtl(namespace: String, serviceId: String, instanceId: String): Mono<Long> {
        return getInstance0(namespace, serviceId, instanceId)
            .map(ServiceInstance::ttlAt)
    }

    override fun addTopology(producerNamespace: String, producerServiceId: String): Mono<Void> {
        val consumerNamespace: String = ServiceInstanceContext.namespace
        val consumerName = consumerName
        val producerName = getProducerName(producerNamespace, producerServiceId)
        return if (consumerName == producerName) {
            Mono.empty()
        } else redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_TOPOLOGY_ADD, listOf(consumerNamespace),
            listOf(consumerName, producerName)
        )
            .then()
    }

    private fun onInstanceChanged(serviceChangedEvent: ServiceChangedEvent) {
        if (log.isInfoEnabled) {
            log.info(
                "onInstanceChanged - instance:[{}] - message:[{}]",
                serviceChangedEvent.instance,
                serviceChangedEvent.event
            )
        }
        val namespacedServiceId = serviceChangedEvent.namespacedServiceId
        val instance = serviceChangedEvent.instance
        val instanceId = instance.instanceId
        val namespace = namespacedServiceId.namespace
        val serviceId = namespacedServiceId.serviceId
        val instancesMono = serviceMapInstances[namespacedServiceId]
        if (instancesMono == null) {
            if (log.isInfoEnabled) {
                log.info(
                    "onInstanceChanged - instance:[{}] - event:[{}] instancesMono is null.",
                    instance,
                    serviceChangedEvent.event
                )
            }
            return
        }
        instancesMono.flatMap { cachedInstances: CopyOnWriteArraySet<ServiceInstance> ->
            val cachedInstance =
                cachedInstances.firstOrNull { it.instanceId == instanceId } ?: ServiceInstance.NOT_FOUND
            if (ServiceInstance.NOT_FOUND == cachedInstance) {
                if (ServiceChangedEvent.Event.REGISTER != serviceChangedEvent.event
                    && ServiceChangedEvent.Event.RENEW != serviceChangedEvent.event
                ) {
                    if (log.isWarnEnabled) {
                        log.warn(
                            "onInstanceChanged - instance:[{}] - event:[{}] not found cached Instance.",
                            instance,
                            serviceChangedEvent.event
                        )
                    }
                    return@flatMap Mono.empty<Any>()
                }
                return@flatMap delegate.getInstance(namespace, serviceId, instanceId)
                    .doOnNext { serviceInstance: ServiceInstance ->
                        if (log.isInfoEnabled) {
                            log.info(
                                "onInstanceChanged - instance:[{}] - event:[{}] add registered Instance.",
                                instance,
                                serviceChangedEvent.event
                            )
                        }
                        cachedInstances.add(serviceInstance)
                    }
            }
            when (serviceChangedEvent.event) {
                ServiceChangedEvent.Event.REGISTER -> {
                    return@flatMap delegate
                        .getInstance(namespace, serviceId, instanceId)
                        .doOnNext { registeredInstance: ServiceInstance ->
                            cachedInstances.add(registeredInstance)
                        }
                }

                ServiceChangedEvent.Event.RENEW -> {
                    if (log.isInfoEnabled) {
                        log.info(
                            "onInstanceChanged - instance:[{}] - event:[{}] setTtlAt.",
                            instance,
                            serviceChangedEvent.event
                        )
                    }
                    return@flatMap delegate
                        .getInstanceTtl(namespace, serviceId, instanceId)
                        .doOnNext { ttlAt ->

                            cachedInstances.add(cachedInstance.copy(ttlAt = ttlAt))
                        }
                }

                ServiceChangedEvent.Event.SET_METADATA -> {
                    if (log.isInfoEnabled) {
                        log.info(
                            "onInstanceChanged - instance:[{}] - event:[{}] setMetadata.",
                            instance,
                            serviceChangedEvent.event
                        )
                    }
                    return@flatMap delegate
                        .getInstance(namespace, serviceId, instanceId)
                        .doOnNext { updatedInstance ->
                            cachedInstances.add(updatedInstance)
                        }
                }

                ServiceChangedEvent.Event.DEREGISTER, ServiceChangedEvent.Event.EXPIRED -> {
                    if (log.isInfoEnabled) {
                        log.info(
                            "onInstanceChanged - instance:[{}] - event:[{}] remove instance.",
                            instance,
                            serviceChangedEvent.event
                        )
                    }
                    cachedInstances.remove(cachedInstance)
                    return@flatMap Mono.empty<Any>()
                }

                else -> return@flatMap Mono.error<Any>(IllegalStateException("Unexpected value: " + serviceChangedEvent.event))
            }
        }.doOnSuccess { hookOnResetInstanceCache(serviceChangedEvent) }.subscribe()
    }

    private class InstanceChangedEventSubscriber(private val serviceDiscovery: ConsistencyRedisServiceDiscovery) :
        BaseSubscriber<ServiceChangedEvent>() {
        override fun hookOnNext(value: ServiceChangedEvent) {
            serviceDiscovery.onInstanceChanged(value)
        }

        override fun hookOnError(throwable: Throwable) {
            if (log.isErrorEnabled) {
                log.error("hookOnError - " + throwable.message, throwable)
            }
        }
    }

    private class ServiceChangedSubscriber(private val serviceDiscovery: ConsistencyRedisServiceDiscovery) :
        BaseSubscriber<ReactiveSubscription.Message<String, String>>() {
        override fun hookOnNext(message: ReactiveSubscription.Message<String, String>) {
            serviceDiscovery.onServiceChanged(message)
        }

        override fun hookOnError(throwable: Throwable) {
            if (log.isErrorEnabled) {
                log.error("hookOnError - " + throwable.message, throwable)
            }
        }
    }
}
