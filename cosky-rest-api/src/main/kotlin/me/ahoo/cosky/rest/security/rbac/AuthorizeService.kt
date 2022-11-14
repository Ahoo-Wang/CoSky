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
package me.ahoo.cosky.rest.security.rbac

import io.jsonwebtoken.ExpiredJwtException
import me.ahoo.cosky.rest.security.JwtProvider
import me.ahoo.cosky.rest.security.TokenExpiredException
import me.ahoo.cosky.rest.security.annotation.AdminResource
import me.ahoo.cosky.rest.security.annotation.AllowAnonymous
import me.ahoo.cosky.rest.security.annotation.OwnerResource
import me.ahoo.cosky.rest.security.rbac.Action.Companion.ofHttpMethod
import me.ahoo.cosky.rest.security.user.User
import me.ahoo.cosky.rest.support.RequestPathPrefix
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Service
import org.springframework.web.method.HandlerMethod
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Authorize Service.
 *
 * @author ahoo wang
 */
@Service
class AuthorizeService(
    private val rbacService: RbacService,
    private val jwtProvider: JwtProvider,
    private val requestMappingHandlerMapping: RequestMappingHandlerMapping
) {
    @Throws(TokenExpiredException::class)
    fun authorize(exchange: ServerWebExchange): Mono<AuthorizeResult> {
        return requestMappingHandlerMapping
            .getHandler(exchange)
            .ofType(HandlerMethod::class.java)
            .flatMap { authorize(exchange, it) }
            .defaultIfEmpty(AuthorizeResult.UNAUTHORIZED)
    }

    private fun authorize(exchange: ServerWebExchange, handlerMethod: HandlerMethod): Mono<AuthorizeResult> {
        val request = exchange.request
        val accessToken = getToken(request)
        if (hasAnnotation(handlerMethod, AllowAnonymous::class.java)) {
            return Mono.just(AuthorizeResult.ALLOW_ANONYMOUS)
        }
        if (!accessToken.isPresent) {
            return Mono.just(AuthorizeResult.UNAUTHORIZED)
        }
        val user: User
        try {
            user = jwtProvider.authorize(accessToken.get())
            exchange.attributes[CURRENT_USER_KEY] = user
        } catch (expiredJwtException: ExpiredJwtException) {
            if (log.isInfoEnabled) {
                log.info(expiredJwtException.message, expiredJwtException)
            }
            return Mono.just(AuthorizeResult.UNAUTHORIZED)
        }
        if (hasAnnotation(handlerMethod, AllowAnonymous::class.java)) {
            return Mono.just(AuthorizeResult.ofAuthorized(user))
        }
        if (user.isAdmin) {
            return Mono.just(AuthorizeResult.ofAuthorized(user))
        }
        if (hasAnnotation(handlerMethod, OwnerResource::class.java)) {
            return Mono.just(AuthorizeResult.ofAuthorized(user))
        }
        if (hasAnnotation(handlerMethod, AdminResource::class.java)) {
            return Mono.just(AuthorizeResult.ofForbidden(user))
        }
        val requestPath = request.path.value()
        val namespace = resolveNamespace(requestPath)
        val requestAction = ResourceAction(namespace, ofHttpMethod(Objects.requireNonNull(request.method)))
        return checkRolePermissions(user, requestAction)
            .map { result: Boolean ->
                if (result) {
                    return@map AuthorizeResult.ofAuthorized(user)
                }
                AuthorizeResult.ofForbidden(user)
            }
    }

    private fun resolveNamespace(requestPath: String): String {
        val namespace = requestPath.substring(RequestPathPrefix.NAMESPACES_PREFIX.length + 1)
        val splitIdx = namespace.indexOf("/")
        return if (splitIdx > 0) {
            namespace.substring(0, splitIdx)
        } else {
            namespace
        }
    }

    fun checkRolePermissions(user: User, requestAction: ResourceAction): Mono<Boolean> {
        return Flux.fromIterable(user.roleBind)
            .flatMap { roleName: String ->
                rbacService.getRole(
                    roleName
                )
            }
            .any { role: Role ->
                role.check(
                    requestAction
                )
            }
    }

    class AuthorizeResult(val isAuthorized: Boolean, val status: HttpStatus, val identity: User?) {

        companion object {
            val ALLOW_ANONYMOUS = AuthorizeResult(true, HttpStatus.OK, null)
            val UNAUTHORIZED = AuthorizeResult(false, HttpStatus.UNAUTHORIZED, null)
            fun ofAuthorized(identity: User): AuthorizeResult {
                return AuthorizeResult(true, HttpStatus.OK, identity)
            }

            fun ofForbidden(identity: User): AuthorizeResult {
                return AuthorizeResult(false, HttpStatus.FORBIDDEN, identity)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AuthorizeService::class.java)
        const val CURRENT_USER_KEY = "cosky.currentUser"
        const val AUTH_HEADER = "Authorization"
        const val QUERY_PARAM_TOKEN = "token"
        fun getToken(request: ServerHttpRequest): Optional<String> {
            val headerToken = request.headers.getFirst(AUTH_HEADER)

            if (!headerToken.isNullOrEmpty()) {
                return Optional.of(headerToken)
            }
            val queryToken = request.queryParams.getFirst(QUERY_PARAM_TOKEN)
            return if (!queryToken.isNullOrEmpty()) {
                Optional.of(queryToken)
            } else {
                Optional.empty()
            }
        }

        fun getUserOfRequest(serverWebExchange: ServerWebExchange): Optional<User> {
            val user = serverWebExchange.getAttribute<User>(CURRENT_USER_KEY)
            return Optional.ofNullable(user)
        }

        fun getRequiredUserOfRequest(serverWebExchange: ServerWebExchange): User {
            return getUserOfRequest(serverWebExchange)
                .orElseThrow { SecurityException("UNAUTHORIZED") }
        }

        fun hasAnnotation(handlerMethod: HandlerMethod, annotationType: Class<out Annotation>): Boolean {
            return (
                AnnotatedElementUtils.hasAnnotation(handlerMethod.beanType, annotationType) ||
                    handlerMethod.hasMethodAnnotation(annotationType)
                )
        }
    }
}
