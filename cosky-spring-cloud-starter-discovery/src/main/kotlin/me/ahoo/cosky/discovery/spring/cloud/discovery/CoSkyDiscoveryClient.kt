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

import me.ahoo.cosky.discovery.ServiceDiscovery
import me.ahoo.cosky.discovery.spring.cloud.discovery.CoSkyServiceInstance.Companion.asSpringInstances
import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.client.discovery.DiscoveryClient

/**
 * CoSky Discovery Client.
 *
 * @author ahoo wang
 */
class CoSkyDiscoveryClient(
    private val serviceDiscovery: ServiceDiscovery,
    private val coSkyDiscoveryProperties: CoSkyDiscoveryProperties,
    private val namespace: String
) : DiscoveryClient {
    override fun description(): String {
        return "CoSky Discovery Client"
    }

    override fun getInstances(serviceId: String): List<ServiceInstance> {
        return serviceDiscovery.getInstances(namespace, serviceId)
            .map { instances -> instances.asSpringInstances(coSkyDiscoveryProperties) }
            .block(coSkyDiscoveryProperties.timeout)!!
    }

    override fun getServices(): List<String> {
        return serviceDiscovery.getServices(namespace).block(coSkyDiscoveryProperties.timeout)!!
    }

    override fun getOrder(): Int {
        return coSkyDiscoveryProperties.order
    }
}
