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

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.v1.getRequest
import me.ahoo.cosky.server.RunningServer
import me.ahoo.cosky.store.key
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The whole server without Redis, as `--dev` starts it, and what it reports about itself.
 */
class DevelopmentModeTest {
    @Test
    fun `the server runs on the memory store, and reports its health and metrics`() = runBlocking<Unit> {
        val port = ServerSocket(0).use { it.localPort }
        val properties = mapOf(
            "cosky.store.type" to "memory",
            "cosky.auth.enabled" to "false",
            "server.port" to port,
        )
        RunningServer(properties).use { server ->
            withTimeout(30.seconds) {
                server.createNamespace("trial")
                server.putConfig("trial", "a", "1")
                val request = getRequest {
                    key = RunningServer.config("trial", "a", "1").key
                    minRevision = 1
                }
                server.resources.get(request).config.content.assert().isEqualTo("1")

                val http = HttpClient.newHttpClient()
                fun get(path: String) = http.send(
                    HttpRequest.newBuilder(URI("http://localhost:$port$path")).build(),
                    BodyHandlers.ofString(),
                )
                // Asking starts the loading of a namespace nobody has asked for yet, like default.
                while (get("/actuator/health/readiness").statusCode() != 200) {
                    delay(50.milliseconds)
                }
                get("/actuator/health/liveness").statusCode().assert().isEqualTo(200)
                val metrics = get("/actuator/prometheus")
                metrics.statusCode().assert().isEqualTo(200)
                metrics.body().assert().contains("cosky_store_apply_seconds")
                metrics.body().assert().contains("cosky_cache_revision")
                get("/v1/namespaces/trial/configs/a").statusCode().assert().isEqualTo(200)
            }
        }
    }
}
