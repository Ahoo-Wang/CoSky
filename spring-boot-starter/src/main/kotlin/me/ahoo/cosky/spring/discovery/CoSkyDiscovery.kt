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

package me.ahoo.cosky.spring.discovery

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.client.CoSkyClient
import me.ahoo.cosky.spring.CoSkyProperties
import org.springframework.cloud.client.DefaultServiceInstance
import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier
import reactor.core.publisher.Flux

private const val DESCRIPTION = "CoSky"
private const val SECURE_SCHEMES = "https,grpcs,wss"

internal fun Resource.toServiceInstance(): ServiceInstance {
    val endpoint = instance.endpoint
    return DefaultServiceInstance(
        meta.name,
        instance.service,
        endpoint.host,
        endpoint.port,
        endpoint.scheme in SECURE_SCHEMES.split(","),
        instance.metadataMap,
    )
}

/**
 * Reads the local view, which keeps following the server. Only the first read of a service waits for the view to be
 * known, so that an application starting up does not take an unknown service for an empty one.
 */
class CoSkyDiscoveryClient(client: CoSkyClient, private val properties: CoSkyProperties) : DiscoveryClient {
    private val instances = client.namespace(properties.namespace).instances

    override fun description(): String = DESCRIPTION

    override fun getInstances(serviceId: String): List<ServiceInstance> = runBlocking(VirtualThreads.dispatcher) {
        withTimeoutOrNull(properties.discoveryLoadTimeout) { instances.load(serviceId) }.orEmpty()
            .map { it.toServiceInstance() }
    }

    override fun getServices(): List<String> = runBlocking(VirtualThreads.dispatcher) {
        withTimeoutOrNull(properties.discoveryLoadTimeout) { instances.services() }.orEmpty().toList()
    }
}

class CoSkyReactiveDiscoveryClient(client: CoSkyClient, private val properties: CoSkyProperties) :
    ReactiveDiscoveryClient {
    private val instances = client.namespace(properties.namespace).instances

    override fun description(): String = DESCRIPTION

    override fun getInstances(serviceId: String): Flux<ServiceInstance> = mono(VirtualThreads.dispatcher) {
        withTimeoutOrNull(properties.discoveryLoadTimeout) { instances.load(serviceId) }.orEmpty()
            .map { it.toServiceInstance() }
    }.flatMapIterable { it }

    override fun getServices(): Flux<String> = mono(VirtualThreads.dispatcher) {
        withTimeoutOrNull(properties.discoveryLoadTimeout) { instances.services() }.orEmpty().toList()
    }.flatMapIterable { it }
}

/**
 * Pushes every change of a service's view to Spring Cloud LoadBalancer, which owns the balancing algorithm
 * (design §7.7).
 */
class CoSkyServiceInstanceListSupplier(private val service: String, client: CoSkyClient, properties: CoSkyProperties) :
    ServiceInstanceListSupplier {
    private val view = client.namespace(properties.namespace).instances.watch(service)

    override fun getServiceId(): String = service

    override fun get(): Flux<List<ServiceInstance>> =
        view.map { instances -> instances.map { it.toServiceInstance() } }.asFlux(VirtualThreads.dispatcher)
}
