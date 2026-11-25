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

import com.google.gson.JsonParser
import me.ahoo.cosky.server.auth.Tokens
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Every failure of the REST API is problem details (RFC 9457), CoSky's own and the framework's alike.
 */
class ProblemDetailsTest {
    private val http = HttpClient.newHttpClient()

    private fun get(port: Int, path: String): HttpResponse<String> = http.send(
        HttpRequest.newBuilder(URI("http://localhost:$port$path")).build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    @Test
    fun `a reason of CoSky's, and a path that does not exist, both answer problem details`() {
        val secured = mapOf(
            "cosky.store.type" to CoSkyProperties.MEMORY,
            "cosky.auth.enabled" to "true",
            "cosky.auth.bootstrap-token" to Tokens.generate().plaintext,
        )

        RunningServer(secured).use { server ->
            val unauthenticated = get(server.httpPort, "/v1/me")
            unauthenticated.statusCode().assert().isEqualTo(UNAUTHORIZED)
            unauthenticated.headers().firstValue("Content-Type").get().assert().isEqualTo(PROBLEM)
            val problem = JsonParser.parseString(unauthenticated.body()).asJsonObject
            problem["reason"].asString.assert().isEqualTo("ERROR_REASON_UNAUTHENTICATED")
            problem["instance"].asString.assert().isEqualTo("/v1/me")

            val missing = get(server.httpPort, "/v1/nothing-here")
            missing.statusCode().assert().isEqualTo(NOT_FOUND)
            missing.headers().firstValue("Content-Type").get().assert().isEqualTo(PROBLEM)
            JsonParser.parseString(missing.body()).asJsonObject["status"].asInt.assert().isEqualTo(NOT_FOUND)
        }
    }

    @Test
    fun `a body larger than any request can need is refused as problem details`() {
        val development = mapOf("cosky.store.type" to CoSkyProperties.MEMORY, "cosky.auth.enabled" to "false")

        RunningServer(development).use { server ->
            val body = """{"config":{"format":"CONFIG_FORMAT_TEXT","content":"a"}}""".padEnd(OVERSIZED)
            val put = HttpRequest.newBuilder(URI("http://localhost:${server.httpPort}/v1/namespaces/default/configs/a"))
                .header("Content-Type", "application/json")
                // Spares sending what is refused anyway.
                .expectContinue(true)
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val refused = http.send(put, HttpResponse.BodyHandlers.ofString())

            refused.statusCode().assert().isEqualTo(BAD_REQUEST)
            refused.headers().firstValue("Content-Type").get().assert().isEqualTo(PROBLEM)
            val violation = JsonParser.parseString(refused.body()).asJsonObject
                .getAsJsonObject("badRequest").getAsJsonArray("violations").single().asJsonObject
            violation["field"].asString.assert().isEqualTo("resource")
            violation["code"].asString.assert().isEqualTo("VIOLATION_CODE_TOO_LONG")
        }
    }

    private companion object {
        const val BAD_REQUEST = 400
        const val UNAUTHORIZED = 401
        const val NOT_FOUND = 404
        const val PROBLEM = "application/problem+json"

        /** Past the 12 MiB the largest resource can take in JSON. */
        const val OVERSIZED = 13 * 1024 * 1024
    }
}
