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

import reactor.core.publisher.Mono

/**
 * Service Topology.
 *
 * @author ahoo wang
 */
interface ServiceTopology {
    fun addTopology(producerNamespace: String, producerServiceId: String): Mono<Void>
    fun getTopology(namespace: String): Mono<Map<String, Set<String>>>

    companion object {
        val NO_OP: ServiceTopology = object : ServiceTopology {
            override fun addTopology(producerNamespace: String, producerServiceId: String): Mono<Void> {
                return Mono.empty()
            }

            override fun getTopology(namespace: String): Mono<Map<String, Set<String>>> {
                return Mono.empty()
            }
        }

        @JvmStatic
        val consumerName: String
            get() {
                return if (ServiceInstanceContext.serviceInstance == ServiceInstance.NOT_FOUND) {
                    DEFAULT_CONSUMER_NAME
                } else {
                    ServiceInstanceContext.serviceInstance.serviceId
                }
            }

        @JvmStatic
        fun getProducerName(producerNamespace: String, producerServiceId: String): String {
            val consumerNamespace = ServiceInstanceContext.namespace
            return if (producerNamespace == consumerNamespace) {
                producerServiceId
            } else {
                "$producerNamespace.$producerServiceId"
            }
        }

        const val DEFAULT_CONSUMER_NAME = "_Client_"
    }
}
