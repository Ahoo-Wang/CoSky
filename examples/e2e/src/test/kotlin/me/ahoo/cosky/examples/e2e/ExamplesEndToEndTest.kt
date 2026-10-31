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

package me.ahoo.cosky.examples.e2e

import com.google.gson.JsonParser
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * What `examples/README.md` says, with each part as a process of its own.
 */
class ExamplesEndToEndTest {
    private val http = HttpClient.newHttpClient()
    private val processes = mutableListOf<Process>()
    private val serverPort = freePort()
    private val grpcPort = freePort()
    private val consumerPort = freePort()

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private fun start(name: String, mainClass: String, vararg arguments: String): Process {
        val logs = File(System.getProperty("cosky.logs")).apply { mkdirs() }
        val java = File(System.getProperty("java.home"), "bin/java").path
        return ProcessBuilder(
            listOf(java, "-cp", System.getProperty("cosky.classpath.$name"), mainClass) + arguments,
        )
            .redirectErrorStream(true)
            .redirectOutput(File(logs, "$name.log"))
            .start()
            .also { processes += it }
    }

    private fun startExample(name: String, mainClass: String, vararg arguments: String): Process = start(
        name,
        mainClass,
        "--cosky.endpoints=localhost:$grpcPort",
        "--cosky.snapshot.enabled=false",
        *arguments,
    )

    @AfterEach
    fun stop() {
        processes.forEach { it.destroy() }
        processes.forEach {
            if (!it.waitFor(TERMINATION_SECONDS, TimeUnit.SECONDS)) {
                it.destroyForcibly()
            }
        }
    }

    private fun get(url: String): HttpResponse<String> = http.send(
        HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(REQUEST_SECONDS)).build(),
        BodyHandlers.ofString(),
    )

    private fun putConfig(content: String) {
        val body = """{"config":{"format":"CONFIG_FORMAT_YAML","content":"$content"}}"""
        val response = http.send(
            HttpRequest.newBuilder(URI("http://localhost:$serverPort/v1/namespaces/default/configs/provider.yaml"))
                .header("Content-Type", "application/json")
                .PUT(BodyPublishers.ofString(body))
                .build(),
            BodyHandlers.ofString(),
        )
        response.statusCode().assert().isEqualTo(HTTP_OK)
    }

    private fun eventually(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (!runCatching(condition).getOrDefault(false)) {
            check(System.nanoTime() < deadline) {
                "Timed out waiting for $what. The logs are in ${System.getProperty("cosky.logs")}."
            }
            Thread.sleep(POLL_MILLIS)
        }
    }

    private fun call(): HttpResponse<String> = get("http://localhost:$consumerPort/call/World")

    @Test
    fun `the readme is true`() {
        start(
            "server",
            "me.ahoo.cosky.server.CoSkyServerApplicationKt",
            "--dev",
            "--server.port=$serverPort",
            "--spring.grpc.server.port=$grpcPort",
            "--cosky.view.topology-flush-interval=1s",
        )
        eventually("the server to be ready") {
            get("http://localhost:$serverPort/actuator/health/readiness").statusCode() == HTTP_OK
        }
        putConfig("example:\\n  greeting: Bonjour")

        val provider = startExample(
            "provider",
            "me.ahoo.cosky.examples.provider.ProviderApplicationKt",
            "--server.port=0",
        )
        startExample(
            "consumer",
            "me.ahoo.cosky.examples.consumer.ConsumerApplicationKt",
            "--server.port=$consumerPort",
        )
        eventually("the consumer to reach the provider") { call().body() == "Bonjour, World" }

        putConfig("example:\\n  greeting: Hi")
        eventually("the changed Config to take effect") { call().body() == "Hi, World" }

        eventually("the topology to show who calls whom") {
            val body = get("http://localhost:$serverPort/v1/namespaces/default/views/topology").body()
            val edges = JsonParser.parseString(body).asJsonObject.getAsJsonArray("edges")
            edges != null && edges.any { edge ->
                val fields = edge.asJsonObject
                fields["consumer"].asString == "consumer" && fields["producer"].asString == "provider"
            }
        }

        provider.destroy()
        provider.waitFor(TERMINATION_SECONDS, TimeUnit.SECONDS).assert().isTrue()
        eventually("the provider to leave") {
            val body = get("http://localhost:$serverPort/v1/namespaces/default/instances?service=provider").body()
            JsonParser.parseString(body).asJsonObject.getAsJsonArray("items").let { it == null || it.isEmpty }
        }
        eventually("the consumer to find no provider") { call().statusCode() != HTTP_OK }
    }

    private companion object {
        const val HTTP_OK = 200
        const val TERMINATION_SECONDS = 15L
        const val REQUEST_SECONDS = 5L
        const val WAIT_SECONDS = 60L
        const val POLL_MILLIS = 200L
    }
}
