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

import me.ahoo.cosky.server.auth.Tokens
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * One image holds the server and the Dashboard (design D11): anyone may load the page, and nothing else changes.
 */
class DashboardHostingTest {
    private val http = HttpClient.newHttpClient()

    private fun get(port: Int, path: String): HttpResponse<String> = http.send(
        HttpRequest.newBuilder(URI("http://localhost:$port$path")).build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    @Test
    fun `the page loads without signing in, and the API still asks for it`() {
        val port = ServerSocket(0).use { it.localPort }
        val secured = mapOf(
            "server.port" to port,
            "cosky.store.type" to CoSkyProperties.MEMORY,
            "cosky.auth.enabled" to "true",
            "cosky.auth.bootstrap-token" to Tokens.generate().plaintext,
        )

        RunningServer(secured).use {
            val page = get(port, "/")
            page.statusCode().assert().isEqualTo(OK)
            page.body().assert().contains("Dashboard under test")
            page.headers().firstValue("Content-Type").get().assert().startsWith("text/html")

            get(port, "/assets/app.js").statusCode().assert().isEqualTo(OK)
            get(port, "/v1/auth/config").statusCode().assert().isEqualTo(OK)
            get(port, "/v1/me").statusCode().assert().isEqualTo(UNAUTHORIZED)
            get(port, "/actuator/health/liveness").statusCode().assert().isEqualTo(OK)
        }
    }

    private companion object {
        const val OK = 200
        const val UNAUTHORIZED = 401
    }
}
