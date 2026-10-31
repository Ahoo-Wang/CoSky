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

package me.ahoo.cosky.server.observe

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.server.RunningServer
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Both protocols leave spans, which reach a collector over OTLP once an endpoint is set (design §7.9).
 */
class TracingTest {
    @Test
    fun `grpc and http calls reach a collector`() = runBlocking<Unit> {
        // Span names are plain text inside the protobuf, so a collector this small can tell.
        val received = CopyOnWriteArrayList<String>()
        val collector = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
            createContext("/v1/traces") { exchange ->
                received += exchange.requestBody.readAllBytes().toString(Charsets.ISO_8859_1)
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
            }
            start()
        }
        val port = ServerSocket(0).use { it.localPort }
        val properties = mapOf(
            "cosky.store.type" to "memory",
            "cosky.auth.enabled" to "false",
            "server.port" to port,
            "management.tracing.sampling.probability" to "1.0",
            "management.opentelemetry.tracing.export.otlp.endpoint" to
                "http://localhost:${collector.address.port}/v1/traces",
        )
        try {
            RunningServer(properties).use { server ->
                withTimeout(30.seconds) {
                    server.createNamespace("traced")
                    HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder(URI("http://localhost:$port/v1/namespaces/traced/configs")).build(),
                        BodyHandlers.ofString(),
                    ).statusCode().assert().isEqualTo(200)
                    while (!received.any { GRPC_SPAN in it } || !received.any { HTTP_SPAN in it }) {
                        delay(100.milliseconds)
                    }
                }
            }
        } finally {
            collector.stop(0)
        }
    }

    private companion object {
        const val GRPC_SPAN = "cosky.v1.ResourceService/Put"
        const val HTTP_SPAN = "http get /v1/namespaces/{namespace}/configs"
    }
}
