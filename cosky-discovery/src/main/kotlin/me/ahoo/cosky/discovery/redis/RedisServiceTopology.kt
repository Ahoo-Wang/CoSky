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

import me.ahoo.cosky.core.requireValidNamespace
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator.getTopologyIdxKey
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator.getTopologyKey
import me.ahoo.cosky.discovery.NamespacedServiceId
import me.ahoo.cosky.discovery.ServiceTopology
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Mono

/**
 * Redis Service Topology.
 *
 * @param consumer the local service, recorded as the consumer of every producer it discovers.
 *
 * @author ahoo wang
 */
class RedisServiceTopology(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val consumer: NamespacedServiceId
) : ServiceTopology {
    init {
        require(consumer.serviceId.isNotBlank()) { "consumer serviceId must not be blank!" }
    }

    override fun addTopology(producerNamespace: String, producerServiceId: String): Mono<Void> {
        requireValidNamespace(producerNamespace)
        val producerName = getProducerName(producerNamespace, producerServiceId)
        if (consumer.serviceId == producerName) {
            return Mono.empty()
        }
        return redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_TOPOLOGY_ADD,
            listOf(getTopologyIdxKey(consumer.namespace), getTopologyKey(consumer.namespace, consumer.serviceId)),
            listOf(consumer.serviceId, producerName),
        ).then()
    }

    /**
     * Producers in the consumer's namespace are named by service id, others are qualified by their namespace.
     */
    private fun getProducerName(producerNamespace: String, producerServiceId: String): String {
        if (producerNamespace == consumer.namespace) {
            return producerServiceId
        }
        return "$producerNamespace.$producerServiceId"
    }

    override fun getTopology(namespace: String): Mono<Map<String, Set<String>>> {
        requireValidNamespace(namespace)
        return redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_SERVICE_TOPOLOGY_GET,
            listOf(getTopologyIdxKey(namespace)),
            listOf(namespace),
        )
            .next()
            .map { deps ->
                val topology = mutableMapOf<String, Set<String>>()
                var consumerName = ""
                for (dep in deps) {
                    when (dep) {
                        is String -> consumerName = dep
                        is List<*> -> topology[consumerName] = dep.map { it.toString() }.toSet()
                    }
                }
                topology
            }
    }
}
