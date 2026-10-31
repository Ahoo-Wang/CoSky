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

package me.ahoo.cosky.spring.registry

import io.grpc.StatusException
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.v1.Endpoint
import me.ahoo.cosky.client.CoSkyClient
import me.ahoo.cosky.spring.CoSkyProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.web.server.context.WebServerInitializedEvent
import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.client.serviceregistry.AbstractAutoServiceRegistration
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationProperties
import org.springframework.cloud.client.serviceregistry.Registration
import org.springframework.cloud.client.serviceregistry.ServiceRegistry
import org.springframework.context.ApplicationContext
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import me.ahoo.cosky.client.Registration as ClientRegistration

/**
 * This application as an instance of its service. The port is known only once the web server has started.
 */
class CoSkyRegistration(
    private val service: String,
    private val host: String,
    private val secure: Boolean,
    private val metadata: Map<String, String>,
) : Registration {
    private var currentPort = 0

    override fun getServiceId(): String = service

    override fun getHost(): String = host

    override fun getPort(): Int = currentPort

    fun setPort(port: Int) {
        currentPort = port
    }

    override fun isSecure(): Boolean = secure

    override fun getUri(): URI = ServiceInstance.createUri(this)

    override fun getMetadata(): Map<String, String> = metadata

    override fun getScheme(): String = if (secure) "https" else "http"
}

class CoSkyServiceRegistry(
    private val client: CoSkyClient,
    private val properties: CoSkyProperties,
) : ServiceRegistry<CoSkyRegistration> {
    private companion object {
        val log = LoggerFactory.getLogger(CoSkyServiceRegistry::class.java)
    }

    private val registered = ConcurrentHashMap<CoSkyRegistration, ClientRegistration>()

    override fun register(registration: CoSkyRegistration?) {
        checkNotNull(registration)
        val endpoint = Endpoint.newBuilder()
            .setScheme(registration.scheme)
            .setHost(registration.host)
            .setPort(registration.port)
            .build()
        val instances = client.namespace(properties.namespace).instances
        registered[registration] = runBlocking(VirtualThreads.dispatcher) {
            instances.register(
                service = registration.serviceId,
                endpoint = endpoint,
                metadata = registration.metadata,
                weight = properties.registry.weight,
            )
        }
    }

    override fun deregister(registration: CoSkyRegistration?) {
        val current = registered.remove(registration) ?: return
        try {
            runBlocking(VirtualThreads.dispatcher) { current.close() }
        } catch (e: StatusException) {
            // This runs while the application shuts down: closing the client gives up the lease, which then expires.
            log.warn("Could not deregister {}: {}", registration?.serviceId, e.status)
        }
    }

    /**
     * Closing the client revokes the lease, so there is nothing to do here.
     */
    override fun close() = Unit

    override fun setStatus(registration: CoSkyRegistration, status: String) {
        throw UnsupportedOperationException("CoSky has no instance status; set the weight to 0 to drain an instance.")
    }

    override fun <T : Any> getStatus(registration: CoSkyRegistration): T {
        throw UnsupportedOperationException("CoSky has no instance status.")
    }
}

/**
 * Registers once the web server is up, and deregisters on shutdown (design §7.7).
 */
class CoSkyAutoServiceRegistration(
    context: ApplicationContext,
    registry: CoSkyServiceRegistry,
    private val registrationProperties: AutoServiceRegistrationProperties,
    private val registration: CoSkyRegistration,
) : AbstractAutoServiceRegistration<CoSkyRegistration>(context, registry, registrationProperties) {
    @Suppress("OVERRIDE_DEPRECATION") // Still abstract in the parent.
    override fun getConfiguration(): Any = registrationProperties

    override fun isEnabled(): Boolean = true

    override fun onApplicationEvent(event: WebServerInitializedEvent) {
        registration.setPort(event.webServer.port)
        super.onApplicationEvent(event)
    }

    override fun getRegistration(): CoSkyRegistration = registration

    /**
     * Never used: a separate management port is not an instance of the service, see [shouldRegisterManagement].
     */
    override fun getManagementRegistration(): CoSkyRegistration = registration

    override fun shouldRegisterManagement(): Boolean = false
}
