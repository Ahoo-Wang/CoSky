package me.ahoo.cosky.rest.security.rbac

import me.ahoo.cosec.api.context.request.Request
import me.ahoo.cosec.context.request.RequestAttributesAppender
import me.ahoo.cosec.webflux.ReactiveRequest
import me.ahoo.cosky.rest.support.RequestPathPrefix
import org.springframework.stereotype.Service
import org.springframework.web.util.pattern.PathPatternParser

@Service
object NamespaceRequestAttributesAppender : RequestAttributesAppender {
    private const val NAMESPACE_KEY = "namespace"
    private val namespacePathPattern =
        PathPatternParser.defaultInstance.parse("${RequestPathPrefix.NAMESPACES_NAMESPACE_PREFIX}/**")

    fun Request.getNamespace(): String? {
        return attributes[NAMESPACE_KEY]
    }

    override fun append(request: Request): Request {
        val reactiveRequest = request as ReactiveRequest
        namespacePathPattern.matchAndExtract(reactiveRequest.delegate.request.path)
            ?.let { pathMatchInfo ->
                pathMatchInfo.uriVariables[NAMESPACE_KEY]?.let {
                    return request.mergeAttributes(mapOf(NAMESPACE_KEY to it))
                }
            }
        return request
    }
}
