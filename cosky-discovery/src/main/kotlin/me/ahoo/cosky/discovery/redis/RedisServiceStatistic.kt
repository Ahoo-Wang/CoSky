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

import me.ahoo.cosky.discovery.DiscoveryKeyGenerator.getServiceStatKey
import me.ahoo.cosky.discovery.InstanceChangedEvent
import me.ahoo.cosky.discovery.InstanceEventListenerContainer
import me.ahoo.cosky.discovery.NamespacedServiceId
import me.ahoo.cosky.discovery.ServiceStat
import me.ahoo.cosky.discovery.ServiceStatistic
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

/**
 * Redis Service Statistic.
 *
 * @author ahoo wang
 */
class RedisServiceStatistic(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val instanceEventListenerContainer: InstanceEventListenerContainer
) : ServiceStatistic {
    companion object {
        private val log = LoggerFactory.getLogger(RedisServiceStatistic::class.java)
    }

    private val listenedNamespaces = ConcurrentHashMap<String, Disposable>()
    private fun startListeningServiceInstancesOfNamespace(namespace: String) {
        listenedNamespaces.computeIfAbsent(namespace) {
            instanceEventListenerContainer.listen(NamespacedServiceId(namespace, ""))
                .doOnNext {
                    instanceChanged(it)
                }
                .subscribe()
        }
    }

    private fun instanceChanged(event: InstanceChangedEvent) {
        if (log.isDebugEnabled) {
            log.debug("instanceChanged - event:[{}]", event)
        }
        if (InstanceChangedEvent.Event.RENEW == event.event) {
            return
        }
        statServiceInternal(event.namespacedServiceId.namespace, event.namespacedServiceId.serviceId).subscribe()
    }

    override fun statService(namespace: String): Mono<Void> {
        startListeningServiceInstancesOfNamespace(namespace)
        return statServiceInternal(namespace, null)
    }

    override fun statService(namespace: String, serviceId: String): Mono<Void> {
        return statServiceInternal(namespace, serviceId)
    }

    private fun statServiceInternal(namespace: String, serviceId: String?): Mono<Void> {
        require(namespace.isNotBlank()) { "namespace can not be blank!" }
        if (log.isDebugEnabled) {
            log.debug("statService  @ namespace:[{}].", namespace)
        }
        val values: List<String> = if (!serviceId.isNullOrEmpty()) {
            listOf(serviceId)
        } else {
            listOf()
        }
        return redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_SERVICE_STAT,
            listOf(namespace),
            values
        ).then()
    }

    fun countService(namespace: String): Mono<Long> {
        require(namespace.isNotBlank()) { "namespace can not be blank!" }
        val serviceIdxStatKey = getServiceStatKey(namespace)
        return redisTemplate
            .opsForHash<Any, Any>()
            .size(serviceIdxStatKey)
    }

    override fun getServiceStats(namespace: String): Flux<ServiceStat> {
        require(namespace.isNotBlank()) { "namespace can not be blank!" }
        val serviceIdxStatKey = getServiceStatKey(namespace)
        return redisTemplate
            .opsForHash<String, String>().entries(serviceIdxStatKey)
            .map { (key, value) ->
                ServiceStat(key, value.toInt())
            }
    }

    override fun getInstanceCount(namespace: String): Mono<Long> {
        return redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_INSTANCE_COUNT_STAT,
            listOf(namespace)
        ).next()
    }
}
