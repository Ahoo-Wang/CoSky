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

package me.ahoo.cosky.server

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.springframework.core.env.Environment
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Actuator on a port of its own answers there alone, as only the probes and Prometheus are meant to reach it; the API
 * and the Dashboard stay on theirs (design §7.4).
 */
class ManagementPortTest {
    private val http = HttpClient.newHttpClient()

    @Test
    fun `Actuator's own port answers nothing but Actuator, and the API's port nothing of Actuator`() {
        RunningServer(DEVELOPMENT + ("management.server.port" to 0)).use { server ->
            val management = server.bean(Environment::class.java)
                .getRequiredProperty("local.management.port", Int::class.java)

            statusOf(management, LIVENESS).assert().isEqualTo(OK)
            statusOf(management, "/actuator/prometheus").assert().isEqualTo(OK)
            statusOf(management, CONFIGS).assert().isEqualTo(NOT_FOUND)
            statusOf(management, "/").assert().isEqualTo(NOT_FOUND)

            statusOf(server.httpPort, CONFIGS).assert().isEqualTo(OK)
            statusOf(server.httpPort, LIVENESS).assert().isEqualTo(NOT_FOUND)
        }
    }

    @Test
    fun `on one port, as by default, Actuator and the API share it`() {
        RunningServer(DEVELOPMENT).use { server ->
            statusOf(server.httpPort, LIVENESS).assert().isEqualTo(OK)
            statusOf(server.httpPort, CONFIGS).assert().isEqualTo(OK)
        }
    }

    private fun statusOf(port: Int, path: String): Int {
        val request = HttpRequest.newBuilder(URI("http://localhost:$port$path")).build()
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
    }

    private companion object {
        val DEVELOPMENT: Map<String, Any> =
            mapOf("cosky.store.type" to CoSkyProperties.MEMORY, "cosky.auth.enabled" to "false")
        const val LIVENESS = "/actuator/health/liveness"
        const val CONFIGS = "/v1/namespaces/default/configs"
        const val OK = 200
        const val NOT_FOUND = 404
    }
}
