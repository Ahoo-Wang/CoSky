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
package me.ahoo.cosky.discovery

import io.github.oshai.kotlinlogging.KotlinLogging
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

/**
 * Records the current service as a consumer of every service it discovers instances of.
 *
 * Only the application-facing discovery should be wrapped; administrative reads must not pollute the topology.
 *
 * @author ahoo wang
 */
class TopologyRecordingServiceDiscovery(
    private val delegate: ServiceDiscovery,
    private val serviceTopology: ServiceTopology
) : ServiceDiscovery by delegate {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val recorded: MutableSet<NamespacedServiceId> = ConcurrentHashMap.newKeySet()

    override fun getInstances(namespace: String, serviceId: String): Mono<List<ServiceInstance>> {
        recordTopology(NamespacedServiceId(namespace, serviceId))
        return delegate.getInstances(namespace, serviceId)
    }

    private fun recordTopology(producer: NamespacedServiceId) {
        if (!recorded.add(producer)) {
            return
        }
        serviceTopology.addTopology(producer.namespace, producer.serviceId)
            .doOnError {
                log.warn(it) { "Record topology of producer[$producer] failed, will retry on next discovery." }
                recorded.remove(producer)
            }
            .onErrorComplete()
            .subscribe()
    }
}
