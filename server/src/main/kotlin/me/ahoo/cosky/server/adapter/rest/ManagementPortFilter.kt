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

package me.ahoo.cosky.server.adapter.rest

import org.springframework.boot.web.server.context.WebServerInitializedEvent
import org.springframework.context.ApplicationListener
import org.springframework.http.HttpStatus
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * With Actuator on a port of its own (`management.server.port`), that port answers Actuator alone (design §7.4).
 * Spring's management context finds the router functions of the application's context, so it would serve the API and
 * the Dashboard there too, to whoever only the probes and Prometheus were meant to reach. It finds this filter the same
 * way, and so the filter sees the requests of both ports.
 *
 * @param basePath Actuator's, `management.endpoints.web.base-path`.
 */
class ManagementPortFilter(private val basePath: String) : WebFilter, ApplicationListener<WebServerInitializedEvent> {
    /** The port the management context bound, once it has; none while Actuator shares the application's port. */
    @Volatile
    private var managementPort: Int? = null

    override fun onApplicationEvent(event: WebServerInitializedEvent) {
        if (event.applicationContext.serverNamespace == MANAGEMENT) {
            managementPort = event.webServer.port
        }
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val port = managementPort
        if (port == null || exchange.request.localAddress?.port != port || isActuator(exchange)) {
            return chain.filter(exchange)
        }
        exchange.response.statusCode = HttpStatus.NOT_FOUND
        return exchange.response.setComplete()
    }

    private fun isActuator(exchange: ServerWebExchange): Boolean {
        val path = exchange.request.path.pathWithinApplication().value()
        return path == basePath || path.startsWith("$basePath/")
    }

    private companion object {
        /** What Spring Boot names the management context's web server. */
        const val MANAGEMENT = "management"
    }
}
