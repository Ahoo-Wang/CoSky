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
package me.ahoo.cosky.rest.security.audit

import me.ahoo.cosec.webflux.ReactiveAuthorizationFilter.Companion.REACTIVE_AUTHORIZATION_FILTER_ORDER
import me.ahoo.cosec.webflux.ServerWebExchanges.getSecurityContext
import me.ahoo.cosky.rest.security.SecurityProperties
import me.ahoo.cosky.rest.security.rbac.Action.Companion.asAction
import me.ahoo.cosky.rest.support.RequestPathPrefix
import org.springframework.core.Ordered
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * Audit Log Handler Interceptor.
 *
 * @author ahoo wang
 */
class AuditLogHandlerInterceptor(
    private val auditService: AuditLogService,
    private val securityProperties: SecurityProperties
) : WebFilter, Ordered {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        return chain.filter(exchange)
            .materialize()
            .doOnNext {
                writeAuditLog(exchange, it.throwable)
            }
            .dematerialize()
    }

    private fun writeAuditLog(exchange: ServerWebExchange, throwable: Throwable?) {
        val request = exchange.request
        val requestAction = requireNotNull(request.method).asAction()
        if (!securityProperties.auditLog.action.check(requestAction)) {
            return
        }
        val requestPath = request.path.value()

        val operator = if (requestPath.startsWith(RequestPathPrefix.AUTHENTICATE_PREFIX)) {
            val operatorPath = requestPath.substring(RequestPathPrefix.AUTHENTICATE_PREFIX.length + 1)
            val splitIdx = operatorPath.indexOf("/")
            if (splitIdx > 0) {
                operatorPath.substring(0, splitIdx)
            } else {
                operatorPath
            }
        } else {
            exchange.getSecurityContext()?.principal?.id.orEmpty()
        }
        val action = request.method.name()
        val ip = requireNotNull(request.remoteAddress).hostString
        val status = requireNotNull(exchange.response.statusCode).value()
        val msg = if (throwable?.message != null) throwable.message!! else ""
        val opTime = System.currentTimeMillis()

        val auditLog = AuditLog(operator, ip, requestPath, action, status, msg, opTime)
        auditService.addLog(auditLog).subscribe()
    }

    override fun getOrder(): Int {
        return REACTIVE_AUTHORIZATION_FILTER_ORDER - 2
    }
}
