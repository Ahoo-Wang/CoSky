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
import me.ahoo.cosky.examples.e2e.Processes.Companion.CONSUMER
import me.ahoo.cosky.examples.e2e.Processes.Companion.HTTP_OK
import me.ahoo.cosky.examples.e2e.Processes.Companion.PROVIDER
import me.ahoo.cosky.examples.e2e.Processes.Companion.SERVER
import me.ahoo.cosky.examples.e2e.Processes.Companion.TERMINATION_SECONDS
import me.ahoo.cosky.examples.e2e.Processes.Companion.freePort
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.http.HttpResponse
import java.util.concurrent.TimeUnit

/**
 * What `examples/README.md` says, with each part as a process of its own.
 */
class ExamplesEndToEndTest {
    private val processes = Processes()
    private val serverPort = freePort()
    private val grpcPort = freePort()
    private val consumerPort = freePort()

    private fun startExample(name: String, mainClass: String, vararg arguments: String): Process = processes.start(
        name,
        name,
        mainClass,
        listOf("--cosky.endpoints=localhost:$grpcPort", "--cosky.snapshot.enabled=false", *arguments),
    )

    @AfterEach
    fun stop() {
        processes.close()
    }

    private fun get(url: String): HttpResponse<String> = processes.get(url)

    private fun putConfig(content: String) {
        val body = """{"config":{"format":"CONFIG_FORMAT_YAML","content":"$content"}}"""
        val response = processes.put("http://localhost:$serverPort/v1/namespaces/default/configs/provider.yaml", body)
        response.statusCode().assert().isEqualTo(HTTP_OK)
    }

    private fun eventually(what: String, condition: () -> Boolean) = processes.eventually(what, condition)

    private fun call(): HttpResponse<String> = get("http://localhost:$consumerPort/call/World")

    @Test
    fun `the readme is true`() {
        processes.start(
            "server",
            "server",
            SERVER,
            listOf(
                "--dev",
                "--server.port=$serverPort",
                "--spring.grpc.server.port=$grpcPort",
                "--cosky.view.topology-flush-interval=1s",
            ),
        )
        eventually("the server to be ready") {
            get("http://localhost:$serverPort/actuator/health/readiness").statusCode() == HTTP_OK
        }
        putConfig("example:\\n  greeting: Bonjour")

        val provider = startExample("provider", PROVIDER, "--server.port=0")
        startExample("consumer", CONSUMER, "--server.port=$consumerPort")
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
}
