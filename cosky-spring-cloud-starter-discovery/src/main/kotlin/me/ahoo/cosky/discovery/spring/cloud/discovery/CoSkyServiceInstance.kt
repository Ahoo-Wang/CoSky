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
package me.ahoo.cosky.discovery.spring.cloud.discovery

import me.ahoo.cosky.discovery.ServiceInstance
import me.ahoo.cosky.discovery.spring.cloud.support.StatusConstants.isUp
import java.net.URI

/**
 * CoSky Service Instance.
 *
 * @author ahoo wang
 */
data class CoSkyServiceInstance(val delegate: ServiceInstance) :
    org.springframework.cloud.client.ServiceInstance {
    companion object {
        /**
         * The metadata key Spring Cloud LoadBalancer's weighted configuration reads the weight from.
         */
        const val WEIGHT_METADATA_KEY = "weight"

        /**
         * The instances Spring Cloud clients may call, as Spring Cloud instances.
         */
        fun List<ServiceInstance>.asSpringInstances(
            properties: CoSkyDiscoveryProperties
        ): List<org.springframework.cloud.client.ServiceInstance> {
            val candidates = if (properties.filterOnlyUpInstances) filter { it.isUp() } else this
            return candidates.map { CoSkyServiceInstance(it) }
        }
    }

    private val springMetadata: Map<String, String> =
        delegate.metadata + (WEIGHT_METADATA_KEY to delegate.weight.toString())

    override fun getInstanceId(): String {
        return delegate.instanceId
    }

    override fun getServiceId(): String {
        return delegate.serviceId
    }

    override fun getHost(): String {
        return delegate.host
    }

    override fun getPort(): Int {
        return delegate.port
    }

    override fun isSecure(): Boolean {
        return delegate.isSecure
    }

    override fun getUri(): URI {
        return delegate.uri
    }

    override fun getMetadata(): Map<String, String> {
        return springMetadata
    }

    override fun getScheme(): String {
        return delegate.schema
    }
}
