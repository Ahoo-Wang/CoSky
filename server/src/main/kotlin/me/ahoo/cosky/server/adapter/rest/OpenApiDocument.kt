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

import io.swagger.v3.core.util.Yaml31
import io.swagger.v3.oas.models.OpenAPI

/**
 * The REST API's OpenAPI document, generated from the protos when the server is built (design D18) and packed into it.
 * springdoc serves this one rather than a document of its own making, which would have nothing to go on: the routes
 * are functional, and the protos are the contract. Its model reads OpenAPI 3.1, so it leaves out what only 3.2 has:
 * the item schema of the Watch's event stream.
 */
object OpenApiDocument {
    private const val RESOURCE = "/cosky/openapi.yaml"

    fun load(): OpenAPI = checkNotNull(OpenApiDocument::class.java.getResourceAsStream(RESOURCE)) {
        "The OpenAPI document $RESOURCE is missing: the server was built without :api:generateOpenApi."
    }.use { Yaml31.mapper().readValue(it, OpenAPI::class.java) }
}
