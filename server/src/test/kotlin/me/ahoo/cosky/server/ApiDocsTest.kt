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

import io.swagger.v3.core.util.Json31
import io.swagger.v3.oas.models.OpenAPI
import me.ahoo.cosky.server.adapter.rest.OpenApiDocument
import me.ahoo.cosky.server.auth.Tokens
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * The REST API's document, as generated from the protos, and Swagger UI on it: anyone may read them, and the API they
 * describe still asks for a token.
 */
class ApiDocsTest {
    private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()

    private fun get(port: Int, path: String): HttpResponse<String> = http.send(
        HttpRequest.newBuilder(URI("http://localhost:$port$path")).build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    @Test
    fun `the document generated from the protos is served whole, with Swagger UI, without signing in`() {
        val secured = mapOf(
            "cosky.store.type" to CoSkyProperties.MEMORY,
            "cosky.auth.enabled" to "true",
            "cosky.auth.bootstrap-token" to Tokens.generate().plaintext,
        )
        val generated = OpenApiDocument.load()

        RunningServer(secured).use { server ->
            val port = server.httpPort
            val docs = get(port, "/v3/api-docs")
            docs.statusCode().assert().isEqualTo(OK)
            val served = Json31.mapper().readValue(docs.body(), OpenAPI::class.java)
            served.info.title.assert().isEqualTo(generated.info.title)
            served.paths.keys.assert().containsExactlyInAnyOrderElementsOf(generated.paths.keys)
            served.components.schemas.keys.assert().containsExactlyInAnyOrderElementsOf(
                generated.components.schemas.keys
            )
            served.components.securitySchemes.keys.assert().containsExactly("bearerAuth")

            val ui = get(port, "/swagger-ui.html")
            ui.statusCode().assert().isEqualTo(OK)
            ui.body().assert().contains("swagger-ui")
            get(port, "/v3/api-docs/swagger-config").body().assert().contains("/v3/api-docs")

            get(port, "/v1/me").statusCode().assert().isEqualTo(UNAUTHORIZED)
        }
    }

    private companion object {
        const val OK = 200
        const val UNAUTHORIZED = 401
    }
}
