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

import me.ahoo.cosky.core.cache.ReactiveWatchCache
import me.ahoo.cosky.core.cache.WatchCache
import me.ahoo.cosky.core.requireValidNamespace
import me.ahoo.cosky.discovery.InstanceChangedEvent
import me.ahoo.cosky.discovery.InstanceEventListenerContainer
import me.ahoo.cosky.discovery.NamespacedServiceId
import me.ahoo.cosky.discovery.ServiceDiscovery
import me.ahoo.cosky.discovery.ServiceEventListenerContainer
import me.ahoo.cosky.discovery.ServiceInstance
import me.ahoo.cosky.discovery.ServiceInstance.Companion.withTtlAt
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Consistency Redis Service Discovery.
 *
 * Serves services and instances from local caches kept consistent by change events.
 * Instance snapshots are immutable lists: an unchanged snapshot is the very same list instance.
 *
 * @author ahoo wang
 */
class ConsistencyRedisServiceDiscovery(
    private val delegate: ServiceDiscovery,
    serviceEventListenerContainer: ServiceEventListenerContainer,
    instanceEventListenerContainer: InstanceEventListenerContainer,
    resyncInterval: Duration? = ReactiveWatchCache.DEFAULT_RESYNC_INTERVAL,
    retry: Retry = ReactiveWatchCache.DEFAULT_RETRY
) : ServiceDiscovery, AutoCloseable {
    companion object {
        /**
         * @see RedisServiceDiscovery.getInstanceTtl
         */
        private const val TTL_INSTANCE_NOT_EXISTS = -2L
    }

    private val servicesCache: WatchCache<String, List<String>> = ReactiveWatchCache(
        listen = { serviceEventListenerContainer.listen(it) },
        load = { delegate.getServices(it) },
        resyncInterval = resyncInterval,
        retry = retry,
    )

    private val instancesCache: WatchCache<NamespacedServiceId, List<ServiceInstance>> = ReactiveWatchCache(
        listen = { instanceEventListenerContainer.listen(it) },
        load = { delegate.getInstances(it.namespace, it.serviceId) },
        apply = ::applyInstanceChanged,
        resyncInterval = resyncInterval,
        retry = retry,
    )

    private val liveInstances = ConcurrentHashMap<NamespacedServiceId, LiveInstances>()

    override fun getServices(namespace: String): Mono<List<String>> {
        requireValidNamespace(namespace)
        return servicesCache.get(namespace).defaultIfEmpty(emptyList())
    }

    override fun getInstances(namespace: String, serviceId: String): Mono<List<ServiceInstance>> {
        val namespacedServiceId = namespacedServiceId(namespace, serviceId)
        return instancesCache.get(namespacedServiceId)
            .defaultIfEmpty(emptyList())
            .map { liveInstancesOf(namespacedServiceId, it) }
    }

    /**
     * The non-expired [instances], reused for as long as the snapshot is unchanged and none of them expires, so that
     * an expired instance awaiting its reaping does not produce a new list on every read.
     */
    private fun liveInstancesOf(
        namespacedServiceId: NamespacedServiceId,
        instances: List<ServiceInstance>
    ): List<ServiceInstance> {
        val nowSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
        val cached = liveInstances[namespacedServiceId]
        if (cached != null && cached.snapshot === instances && nowSeconds <= cached.validUntil) {
            return cached.live
        }
        val live = if (instances.none { it.isExpiredAt(nowSeconds) }) {
            instances
        } else {
            instances.filterNot { it.isExpiredAt(nowSeconds) }
        }
        val validUntil = live.filter { it.isEphemeral }.minOfOrNull { it.ttlAt } ?: Long.MAX_VALUE
        liveInstances[namespacedServiceId] = LiveInstances(instances, live, validUntil)
        return live
    }

    /**
     * @see ServiceInstance.isExpired
     */
    private fun ServiceInstance.isExpiredAt(nowSeconds: Long): Boolean = isEphemeral && ttlAt < nowSeconds

    private class LiveInstances(
        val snapshot: List<ServiceInstance>,
        val live: List<ServiceInstance>,
        val validUntil: Long
    )

    override fun getInstance(namespace: String, serviceId: String, instanceId: String): Mono<ServiceInstance> {
        require(instanceId.isNotBlank()) { "instanceId must not be blank!" }
        return instancesCache.get(namespacedServiceId(namespace, serviceId))
            .mapNotNull<ServiceInstance> { instances -> instances.firstOrNull { it.instanceId == instanceId } }
            .switchIfEmpty(Mono.defer { delegate.getInstance(namespace, serviceId, instanceId) })
    }

    override fun getInstanceTtl(namespace: String, serviceId: String, instanceId: String): Mono<Long> {
        return getInstance(namespace, serviceId, instanceId).map(ServiceInstance::ttlAt)
    }

    private fun namespacedServiceId(namespace: String, serviceId: String): NamespacedServiceId {
        requireValidNamespace(namespace)
        require(serviceId.isNotBlank()) { "serviceId must not be blank!" }
        return NamespacedServiceId(namespace, serviceId)
    }

    /**
     * Idempotent and read-through: every event is resolved against the current state in Redis.
     */
    private fun applyInstanceChanged(
        serviceId: NamespacedServiceId,
        current: List<ServiceInstance>?,
        changed: InstanceChangedEvent
    ): Mono<List<ServiceInstance>> {
        val instances = current.orEmpty()
        val instanceId = changed.instance.instanceId
        return when (changed.event) {
            InstanceChangedEvent.Event.DEREGISTER,
            InstanceChangedEvent.Event.EXPIRED -> Mono.just(instances.without(instanceId))

            InstanceChangedEvent.Event.RENEW -> {
                val cached = instances.firstOrNull { it.instanceId == instanceId }
                    ?: return refreshInstance(serviceId, instances, instanceId)
                changed.ttlAt?.let { return Mono.just(instances.upsert(cached.withTtlAt(it))) }
                delegate.getInstanceTtl(serviceId.namespace, serviceId.serviceId, instanceId)
                    .map { ttlAt ->
                        if (ttlAt == TTL_INSTANCE_NOT_EXISTS) {
                            instances.without(instanceId)
                        } else {
                            instances.upsert(cached.withTtlAt(ttlAt))
                        }
                    }
            }

            InstanceChangedEvent.Event.REGISTER,
            InstanceChangedEvent.Event.SET_METADATA -> refreshInstance(serviceId, instances, instanceId)
        }
    }

    private fun refreshInstance(
        serviceId: NamespacedServiceId,
        instances: List<ServiceInstance>,
        instanceId: String
    ): Mono<List<ServiceInstance>> {
        return delegate.getInstance(serviceId.namespace, serviceId.serviceId, instanceId)
            .map { instances.upsert(it) }
            .defaultIfEmpty(instances.without(instanceId))
    }

    private fun List<ServiceInstance>.without(instanceId: String): List<ServiceInstance> {
        if (none { it.instanceId == instanceId }) {
            return this
        }
        return filterNot { it.instanceId == instanceId }
    }

    private fun List<ServiceInstance>.upsert(instance: ServiceInstance): List<ServiceInstance> {
        val index = indexOfFirst { it.instanceId == instance.instanceId }
        if (index < 0) {
            return this + instance
        }
        if (this[index] == instance) {
            return this
        }
        return toMutableList().also { it[index] = instance }
    }

    override fun close() {
        servicesCache.close()
        instancesCache.close()
        liveInstances.clear()
    }
}
