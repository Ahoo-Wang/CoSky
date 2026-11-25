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
import me.ahoo.cosky.spring.discovery.toUri
import org.slf4j.LoggerFactory
import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.boot.web.server.context.WebServerInitializedEvent
import org.springframework.cloud.client.serviceregistry.AbstractAutoServiceRegistration
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationProperties
import org.springframework.cloud.client.serviceregistry.Registration
import org.springframework.cloud.client.serviceregistry.ServiceRegistry
import org.springframework.context.ApplicationContext
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import me.ahoo.cosky.client.Registration as ClientRegistration

/**
 * This application as an instance of its service. The port is known only once the web server has started.
 *
 * The host is as the protocol has it, an IPv6 one without brackets; the URI has them.
 *
 * The metadata is a copy of what it is given, which a listener of `InstancePreRegisteredEvent` may add to before the
 * registration is made, as with Spring Cloud's other registries, without changing the configured properties.
 */
class CoSkyRegistration(
    private val service: String,
    private val host: String,
    private val secure: Boolean,
    metadata: Map<String, String>,
) : Registration {
    private val metadata: MutableMap<String, String> = LinkedHashMap(metadata)
    private var currentPort = 0

    override fun getServiceId(): String = service

    override fun getHost(): String = host

    override fun getPort(): Int = currentPort

    fun setPort(port: Int) {
        currentPort = port
    }

    override fun isSecure(): Boolean = secure

    override fun getUri(): URI = toUri()

    override fun getMetadata(): MutableMap<String, String> = metadata

    override fun getScheme(): String = if (secure) "https" else "http"
}

class CoSkyServiceRegistry(
    private val client: CoSkyClient,
    private val properties: CoSkyProperties,
) : ServiceRegistry<CoSkyRegistration> {
    private val registered = ConcurrentHashMap<CoSkyRegistration, ClientRegistration>()

    override fun register(registration: CoSkyRegistration?) {
        checkNotNull(registration)
        val endpoint = Endpoint.newBuilder()
            .setScheme(registration.scheme)
            .setHost(registration.host)
            .setPort(registration.port)
            .build()
        val instances = client.namespace(properties.namespace).instances
        registered[registration] = try {
            runBlocking(VirtualThreads.dispatcher) {
                instances.register(
                    service = registration.serviceId,
                    endpoint = endpoint,
                    metadata = registration.metadata,
                    weight = properties.registry.weight,
                )
            }
        } catch (e: StatusException) {
            if (e.status.code in RegistrationRefusedException.CODES) {
                throw RegistrationRefusedException(registration.serviceId, properties.namespace, e)
            }
            throw e
        }
    }

    /**
     * Deletes the instance. When that fails it stays registered and renewed, and this throws: call it again. Only the
     * handle this call closed is forgotten, not one a registration made meanwhile.
     */
    override fun deregister(registration: CoSkyRegistration?) {
        val current = registered[registration ?: return] ?: return
        try {
            runBlocking(VirtualThreads.dispatcher) { current.close() }
        } catch (e: StatusException) {
            throw IllegalStateException(
                "Could not deregister service ${registration.serviceId} from namespace ${properties.namespace}: " +
                    listOfNotNull(e.status.code.name, e.status.description).joinToString(": ") +
                    ". It stays registered and renewed; call deregister again.",
                e,
            )
        }
        registered.remove(registration, current)
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
 * Registers once the web server is up, and deregisters as the application context starts closing, before the web
 * server stops taking requests (design §7.7).
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

    /**
     * The port of a separate management server is not the service's. The parent leaves its event alone, and so does
     * this, or registering again, after [stop] and [start], would put that port in the instance.
     */
    override fun onApplicationEvent(event: WebServerInitializedEvent) {
        if (!WebServerApplicationContext.hasServerNamespace(event.applicationContext, MANAGEMENT)) {
            registration.setPort(event.webServer.port)
        }
        super.onApplicationEvent(event)
    }

    override fun getRegistration(): CoSkyRegistration = registration

    /**
     * Leaves as soon as the context announces that it is closing, which it does before it stops anything: consumers
     * stop sending requests while the web server, shutting down gracefully, finishes those under way, and only then
     * stops. The parent leaves when it is destroyed, after all that, which would keep the instance listed while its
     * server refuses connections. Spring Cloud's Eureka registry does the same.
     *
     * The parent still leaves on destruction when the context closes without announcing it, such as when it fails to
     * start, and either way only once. Closing the client revokes the lease after both. A child context, such as a
     * separate management server's, announces its own closing here too, which is not the application's.
     *
     * Private, which Spring calls all the same, so that it is no part of the starter's API.
     */
    @Suppress("UnusedPrivateMember") // Spring calls it.
    @EventListener
    private fun onContextClosed(event: ContextClosedEvent) {
        if (event.applicationContext === context) {
            stop()
        }
    }

    /**
     * On shutdown, where a failure is worth a warning only: closing the client, once the web server has stopped, gives
     * up the lease, which then expires.
     */
    override fun deregister() {
        try {
            super.deregister()
        } catch (e: IllegalStateException) {
            log.warn(e.message)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(CoSkyAutoServiceRegistration::class.java)

        /** The server namespace of a separate management server's context. */
        const val MANAGEMENT = "management"
    }

    /**
     * Never used: a separate management port is not an instance of the service, see [shouldRegisterManagement].
     */
    override fun getManagementRegistration(): CoSkyRegistration = registration

    override fun shouldRegisterManagement(): Boolean = false
}
