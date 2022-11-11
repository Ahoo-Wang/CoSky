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
package me.ahoo.cosky.rest.security

import me.ahoo.cosky.rest.security.audit.AuditLog
import me.ahoo.cosky.rest.security.audit.AuditLogService
import me.ahoo.cosky.rest.security.rbac.Action
import me.ahoo.cosky.rest.security.rbac.AuthorizeService
import me.ahoo.cosky.rest.security.rbac.AuthorizeService.AuthorizeResult
import me.ahoo.cosky.rest.support.RequestPathPrefix
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.*

/**
 * Authorize Handler Interceptor.
 *
 * @author ahoo wang
 */
class AuthorizeHandlerInterceptor(
    private val authorizeService: AuthorizeService,
    private val auditService: AuditLogService,
    private val securityProperties: SecurityProperties
) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        return authorize(exchange)
            .flatMap {
                if (it.isAuthorized) {
                    return@flatMap chain.filter(exchange)
                }
                exchange.response.statusCode = it.status
                Mono.empty()
            }
            .doOnSuccess { writeAuditLog(exchange, null) }
            .doOnError { throwable -> writeAuditLog(exchange, throwable) }
    }

    private fun writeAuditLog(exchange: ServerWebExchange, throwable: Throwable?) {
        val request = exchange.request
        val requestAction = Action.ofHttpMethod(Objects.requireNonNull(request.method))
        if (!securityProperties.auditLog.action.check(requestAction)) {
            return
        }
        val requestPath = request.path.value()

        val operator = if (requestPath.startsWith(RequestPathPrefix.AUTHENTICATE_PREFIX)) {
            val operatorPath = requestPath.substring(RequestPathPrefix.AUTHENTICATE_PREFIX.length + 1)
            val splitIdx = operatorPath.indexOf("/")
            if (splitIdx > 0) {
                operatorPath.substring(0, splitIdx)
            } else operatorPath
        } else {
            AuthorizeService.getRequiredUserOfRequest(exchange).username
        }
        val action = request.method!!.name
        val ip = requireNotNull(request.remoteAddress).hostString
        val status = requireNotNull(exchange.response.statusCode).value()
        val msg = if (throwable?.message != null) throwable.message!! else ""
        val opTime = System.currentTimeMillis()

        val auditLog = AuditLog(operator, ip, requestPath, action, status, msg, opTime)
        auditService.addLog(auditLog).subscribe()
    }

    fun authorize(exchange: ServerWebExchange): Mono<AuthorizeResult> {
        val request = exchange.request
        val requestPath = request.path.value()
        return if ((requestPath.startsWith(RequestPathPrefix.DASHBOARD)
                    || requestPath.startsWith(RequestPathPrefix.SWAGGER_UI)
                    || requestPath.startsWith(RequestPathPrefix.SWAGGER_UI_RESOURCE)
                    || requestPath.startsWith("/actuator/health")
                    || requestPath.startsWith("/v3/api-docs"))
            || "/" == requestPath
            || HttpMethod.OPTIONS == request.method
        ) {
            Mono.just(AuthorizeResult.ALLOW_ANONYMOUS)
        } else authorizeService.authorize(exchange)
    }

    companion object {
        private val log = LoggerFactory.getLogger(AuthorizeHandlerInterceptor::class.java)
    }
}
