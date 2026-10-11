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
 * Service Registry.
 *
 * @author ahoo wang
 */
interface ServiceRegistry {
    fun setService(namespace: String, serviceId: String): Mono<Boolean>
    fun removeService(namespace: String, serviceId: String): Mono<Boolean>

    /**
     * 注册实例.
     *
     * @param serviceInstance serviceInstance
     * @return If true, the registration succeeds, otherwise it fails
     */
    fun register(namespace: String, serviceInstance: ServiceInstance): Mono<Boolean>

    /**
     * 服务实例续期.
     *
     * @param serviceInstance serviceInstance
     * @return If true, the renew succeeds, otherwise it fails
     */
    fun renew(namespace: String, serviceInstance: ServiceInstance): Mono<Boolean>

    fun deregister(namespace: String, serviceInstance: ServiceInstance): Mono<Boolean>

    fun deregister(
        namespace: String,
        serviceId: String,
        instanceId: String
    ): Mono<Boolean>

    val registeredEphemeralInstances: Map<NamespacedInstanceId, ServiceInstance>

    /**
     * Sets one metadata entry, keeping the others.
     */
    fun setMetadata(
        namespace: String,
        serviceId: String,
        instanceId: String,
        key: String,
        value: String
    ): Mono<Boolean>

    /**
     * Replaces all metadata of the instance with [metadata].
     */
    fun setMetadata(
        namespace: String,
        serviceId: String,
        instanceId: String,
        metadata: Map<String, String>
    ): Mono<Boolean>

    /**
     * Removes up to [limit] instances of [namespace] whose TTL has passed and publishes their `expired` events.
     *
     * @return how many instances were reaped
     */
    fun reapExpiredInstances(namespace: String, limit: Int = DEFAULT_REAP_LIMIT): Mono<Long>

    companion object {
        const val DEFAULT_REAP_LIMIT = 100
    }
}
