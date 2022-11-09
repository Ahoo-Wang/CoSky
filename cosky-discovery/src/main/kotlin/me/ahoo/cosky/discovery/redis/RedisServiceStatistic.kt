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
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator.getInstanceKeyPatternOfNamespace
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator.getNamespaceOfKey
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator.getServiceStatKey
import me.ahoo.cosky.discovery.Instance
import me.ahoo.cosky.discovery.Instance.Companion.asInstance
import me.ahoo.cosky.discovery.ServiceChangedEvent
import me.ahoo.cosky.discovery.ServiceChangedEvent.Companion.asServiceChangedEvent
import me.ahoo.cosky.discovery.ServiceStat
import me.ahoo.cosky.discovery.ServiceStatistic
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.ReactiveSubscription
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Redis Service Statistic.
 *
 * @author ahoo wang
 */
class RedisServiceStatistic(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val listenerContainer: ReactiveRedisMessageListenerContainer
) : ServiceStatistic {
    companion object {
        private val log = LoggerFactory.getLogger(RedisServiceStatistic::class.java)
    }

    private val listenedNamespaces = ConcurrentHashMap<String, Disposable>()
    private fun startListeningServiceInstancesOfNamespace(namespace: String) {
        listenedNamespaces.computeIfAbsent(namespace) {
            val instancePattern = getInstanceKeyPatternOfNamespace(namespace)
            listenerContainer.receive(PatternTopic.of(instancePattern))
                .doOnNext {
                    instanceChanged(it)
                }
                .subscribe()
        }
    }

    private fun instanceChanged(message: ReactiveSubscription.PatternMessage<String, String, String>) {
        if (log.isInfoEnabled) {
            log.info(
                "instanceChanged - pattern:[{}] - channel:[{}] - message:[{}]",
                message.pattern,
                message.channel,
                message.message
            )
        }
        if (ServiceChangedEvent.Event.RENEW == message.message.asServiceChangedEvent()) {
            return
        }
        val instanceKey = message.channel
        val namespace = getNamespaceOfKey(instanceKey)
        val instanceId = getInstanceIdOfKey(namespace, instanceKey)
        val instance: Instance = instanceId.asInstance()
        val serviceId = instance.serviceId
        statService0(namespace, serviceId).subscribe()
    }

    override fun statService(namespace: String): Mono<Void> {
        startListeningServiceInstancesOfNamespace(namespace)
        return statService0(namespace, null)
    }

    override fun statService(namespace: String, serviceId: String): Mono<Void> {
        return statService0(namespace, serviceId)
    }

    private fun statService0(namespace: String, serviceId: String?): Mono<Void> {
        require(namespace.isNotBlank()) { "namespace can not be blank!" }
        if (log.isInfoEnabled) {
            log.info("statService  @ namespace:[{}].", namespace)
        }
        val values: List<String> = if (!serviceId.isNullOrEmpty()) {
            listOf(serviceId)
        } else {
            listOf()
        }
        return redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_SERVICE_STAT, listOf(namespace),
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
            DiscoveryRedisScripts.SCRIPT_INSTANCE_COUNT_STAT, listOf(namespace)
        )
            .next()
    }

    override fun getTopology(namespace: String): Mono<Map<String, Set<String>>> {
        require(namespace.isNotBlank()) { "namespace can not be blank!" }
        return redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_SERVICE_TOPOLOGY_GET, listOf(namespace)
        )
            .map<Map<String, Set<String>>> { result: List<*> ->
                val deps = result as List<Any>
                val topology: MutableMap<String, Set<String>> = HashMap(deps.size)
                var consumerName = ""
                for (dep in deps) {
                    if (dep is String) {
                        consumerName = dep.toString()
                    }
                    if (dep is List<*>) {
                        topology[consumerName] = HashSet(dep as List<String>)
                    }
                }
                topology
            }
            .next()
    }
}
