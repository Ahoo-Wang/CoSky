package me.ahoo.cosky.discovery.redis

import me.ahoo.cosky.discovery.ServiceInstanceContext
import me.ahoo.cosky.discovery.ServiceTopology
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Mono
import java.util.HashMap
import java.util.HashSet

class RedisServiceTopology(private val redisTemplate: ReactiveStringRedisTemplate) : ServiceTopology {
    override fun addTopology(producerNamespace: String, producerServiceId: String): Mono<Void> {
        val consumerNamespace: String = ServiceInstanceContext.namespace
        val consumerName = ServiceTopology.consumerName
        val producerName = ServiceTopology.getProducerName(producerNamespace, producerServiceId)
        return if (consumerName == producerName) {
            Mono.empty()
        } else {
            redisTemplate.execute(
                DiscoveryRedisScripts.SCRIPT_TOPOLOGY_ADD,
                listOf(consumerNamespace),
                listOf(consumerName, producerName),
            ).then()
        }
    }

    override fun getTopology(namespace: String): Mono<Map<String, Set<String>>> {
        require(namespace.isNotBlank()) { "namespace can not be blank!" }
        @Suppress("UNCHECKED_CAST")
        return redisTemplate.execute(
            DiscoveryRedisScripts.SCRIPT_SERVICE_TOPOLOGY_GET,
            listOf(namespace),
        )
            .map<Map<String, Set<String>>> {
                val deps = it as List<Any>
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
