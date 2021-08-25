/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

package me.ahoo.cosky.rest.security;

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosky.rest.security.audit.AuditLog;
import me.ahoo.cosky.rest.security.audit.AuditLogService;
import me.ahoo.cosky.rest.security.rbac.Action;
import me.ahoo.cosky.rest.security.rbac.RBACService;
import me.ahoo.cosky.rest.security.user.User;
import me.ahoo.cosky.rest.support.RequestPathPrefix;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * @author ahoo wang
 */
@Slf4j
public class AuthorizeHandlerInterceptor implements WebFilter {
    public static final String AUTH_HEADER = "Authorization";
    public static final String QUERY_PARAM_TOKEN = "token";
    private final RBACService rbacService;
    private final AuditLogService auditService;
    private final SecurityProperties securityProperties;
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    public AuthorizeHandlerInterceptor(RBACService rbacService,
                                       AuditLogService auditService,
                                       SecurityProperties securityProperties, RequestMappingHandlerMapping requestMappingHandlerMapping) {
        this.rbacService = rbacService;
        this.auditService = auditService;
        this.securityProperties = securityProperties;
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
    }

    /**
     * Process the Web request and (optionally) delegate to the next
     * {@code WebFilter} through the given {@link WebFilterChain}.
     *
     * @param exchange the current server exchange
     * @param chain    provides a way to delegate to the next filter
     * @return {@code Mono<Void>} to indicate when request processing is complete
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return check(exchange)
                .flatMap(result -> {
                    if (result) {
                        return chain.filter(exchange);
                    }
                    return Mono.empty();
                })
                .doOnSuccess(nil -> writeAuditLog(exchange, null))
                .doOnError(throwable -> writeAuditLog(exchange, throwable));
    }

    private void writeAuditLog(ServerWebExchange exchange, Throwable throwable) {
        ServerHttpRequest request = exchange.getRequest();
        Action requestAction = Action.ofHttpMethod(Objects.requireNonNull(request.getMethod()));

        if (!securityProperties.getAuditLog().getAction().check(requestAction)) {
            return;
        }
        String requestPath = request.getPath().value();

        AuditLog auditLog = new AuditLog();
        if (!requestPath.startsWith(RequestPathPrefix.AUTHENTICATE_PREFIX)) {
            User currentUser = RBACService.getUserOfRequest(exchange);
            assert currentUser != null;
            auditLog.setOperator(currentUser.getUsername());
        }

        auditLog.setResource(requestPath);
        auditLog.setAction(request.getMethod().name());
        auditLog.setIp(Objects.requireNonNull(request.getRemoteAddress()).getHostString());
        auditLog.setStatus(Objects.requireNonNull(exchange.getResponse().getStatusCode()).value());

        if (Objects.nonNull(throwable)) {
            auditLog.setMsg(throwable.getMessage());
        }

        auditLog.setOpTime(System.currentTimeMillis());

        auditService.addLog(auditLog).subscribe();
    }

    public Mono<Boolean> check(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String requestPath = request.getPath().value();

        if (requestPath.startsWith(RequestPathPrefix.DASHBOARD)) {
            return Mono.just(true);
        }

        if (requestPath.startsWith(RequestPathPrefix.AUTHENTICATE_PREFIX)
                || !requestPath.startsWith(RequestPathPrefix.V1)
                || HttpMethod.OPTIONS.equals(request.getMethod())
        ) {
            return Mono.just(true);
        }

        String accessToken = request.getHeaders().getFirst(AUTH_HEADER);
        if (Strings.isNullOrEmpty(accessToken)) {
            accessToken = request.getQueryParams().getFirst(QUERY_PARAM_TOKEN);
        }
        if (Strings.isNullOrEmpty(accessToken)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return Mono.just(false);
        }
        Mono<HandlerMethod> handlerMethodMono = requestMappingHandlerMapping.getHandler(exchange).ofType(HandlerMethod.class);
        return rbacService.authorize(accessToken, exchange, handlerMethodMono)
                .doOnNext(result -> {
                    if (!result) {
                        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    }
                })
                .onErrorResume(TokenExpiredException.class, (tokenExpiredException) -> {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return Mono.just(Boolean.FALSE);
                });
    }
}
