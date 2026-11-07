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

import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.client.CoSkyClient
import me.ahoo.cosky.client.effectiveWeight
import me.ahoo.cosky.spring.CoSkyProperties
import org.springframework.cloud.client.DefaultServiceInstance
import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier
import reactor.core.publisher.Flux
import java.net.URI

private const val DESCRIPTION = "CoSky"
private const val SECURE_SCHEMES = "https,grpcs,wss"
private const val HTTP_PORT = 80
private const val HTTPS_PORT = 443

internal fun Resource.toServiceInstance(): ServiceInstance {
    val endpoint = instance.endpoint
    return CoSkyServiceInstance(
        meta.name,
        instance.service,
        endpoint.host,
        endpoint.port,
        endpoint.scheme in SECURE_SCHEMES.split(","),
        instance.metadataMap,
    )
}

/**
 * The URI [ServiceInstance.createUri] makes, but with an IPv6 host in brackets: Spring writes the host as it is, and
 * `http://2001:db8::1:8080` has neither a host nor a port. [ServiceInstance.getHost] stays as the protocol has it.
 */
internal fun ServiceInstance.toUri(): URI {
    val (scheme, defaultPort) = if (isSecure) "https" to HTTPS_PORT else "http" to HTTP_PORT
    // Unlike parsing one, building a URI from its parts puts an IPv6 host in brackets. A port that is not known yet
    // is the scheme's, as Spring has it.
    return URI(scheme, null, host, port.takeIf { it > 0 } ?: defaultPort, null, null, null)
}

/**
 * Spring's own instance, except for its URI: see [toUri].
 */
private class CoSkyServiceInstance(
    instanceId: String,
    serviceId: String,
    host: String,
    port: Int,
    secure: Boolean,
    metadata: Map<String, String>,
) : DefaultServiceInstance(instanceId, serviceId, host, port, secure, metadata) {
    override fun getUri(): URI = toUri()
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
 * (design §7.7). A drained instance stays out of its list, as it does for the client's own balancer.
 */
class CoSkyServiceInstanceListSupplier(
    private val service: String,
    client: CoSkyClient,
    private val properties: CoSkyProperties,
) : ServiceInstanceListSupplier {
    private val instances = client.namespace(properties.namespace).instances
    private val view = instances.watch(service)

    override fun getServiceId(): String = service

    /**
     * A balancer goes by the first list, so that one waits for the view to be known, as [CoSkyDiscoveryClient] does:
     * the first call of an application must not take an unknown service for an empty one.
     */
    override fun get(): Flux<List<ServiceInstance>> = flow {
        withTimeoutOrNull(properties.discoveryLoadTimeout) { instances.load(service) }?.let { emit(it) }
        emitAll(view)
    }.map { resources -> resources.filter { it.effectiveWeight > 0 }.map { it.toServiceInstance() } }
        .asFlux(VirtualThreads.dispatcher)
}
