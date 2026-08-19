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
package me.ahoo.cosky.rest.security.authorization

import me.ahoo.cosec.webflux.ReactiveRequest
import me.ahoo.cosky.rest.security.authorization.NamespaceRequestAttributesAppender.getNamespace
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import java.net.URI

class NamespaceRequestAttributesAppenderTest {
    @Test
    fun `normalizes namespace before authorization`() {
        val request = reactiveRequest(HttpMethod.GET, "/v1/namespaces/dev/configs")

        NamespaceRequestAttributesAppender.append(request).getNamespace().assert().isEqualTo("{dev}")

        val systemRequest = reactiveRequest(HttpMethod.GET, "/v1/namespaces/cosky-%7Bsystem%7D/configs")
        NamespaceRequestAttributesAppender.append(systemRequest).getNamespace().assert().isEqualTo("cosky-{system}")
    }

    @Test
    fun `normalizes current namespace selection`() {
        val request = reactiveRequest(HttpMethod.PUT, "/v1/namespaces/current/dev")

        NamespaceRequestAttributesAppender.append(request).getNamespace().assert().isEqualTo("{dev}")
    }

    private fun reactiveRequest(method: HttpMethod, path: String): ReactiveRequest {
        val origin = URI.create("http://localhost")
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.method(method, origin.resolve(path)).build(),
        )
        return ReactiveRequest(
            delegate = exchange,
            path = path,
            method = method.name(),
            remoteIp = "127.0.0.1",
            origin = origin,
            referer = origin,
            requestId = "request-id",
        )
    }
}
