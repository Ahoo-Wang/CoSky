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

import me.ahoo.cosec.api.context.request.Request
import me.ahoo.cosec.context.request.RequestAttributesAppender
import me.ahoo.cosec.webflux.ReactiveRequest
import me.ahoo.cosky.rest.support.RequestPathPrefix
import me.ahoo.cosky.rest.support.normalizeNamespace
import org.springframework.stereotype.Service
import org.springframework.web.util.pattern.PathPatternParser

@Service
object NamespaceRequestAttributesAppender : RequestAttributesAppender {
    private const val NAMESPACE_KEY = "namespace"
    private val currentNamespacePathPattern = PathPatternParser.defaultInstance.parse(
        RequestPathPrefix.NAMESPACES_PREFIX + RequestPathPrefix.NAMESPACES_CURRENT_NAMESPACE,
    )
    private val namespacePathPattern =
        PathPatternParser.defaultInstance.parse("${RequestPathPrefix.NAMESPACES_NAMESPACE_PREFIX}/**")

    fun Request.getNamespace(): String? {
        return attributes[NAMESPACE_KEY]
    }

    override fun append(request: Request): Request {
        val reactiveRequest = request as ReactiveRequest
        val requestPath = reactiveRequest.delegate.request.path
        val namespace = currentNamespacePathPattern.matchAndExtract(requestPath)
            ?.uriVariables
            ?.get(NAMESPACE_KEY)
            ?: namespacePathPattern.matchAndExtract(requestPath)
                ?.uriVariables
                ?.get(NAMESPACE_KEY)
            ?: return request
        val normalizedNamespace = try {
            namespace.normalizeNamespace()
        } catch (_: IllegalArgumentException) {
            // Controller validation returns the client error without failing the authorization request parser.
            namespace
        }
        return request.mergeAttributes(mapOf(NAMESPACE_KEY to normalizedNamespace))
    }
}
