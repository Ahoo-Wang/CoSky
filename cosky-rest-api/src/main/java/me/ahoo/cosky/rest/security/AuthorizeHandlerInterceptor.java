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

import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosky.rest.security.audit.AuditLog;
import me.ahoo.cosky.rest.security.audit.AuditLogService;
import me.ahoo.cosky.rest.security.rbac.Action;
import me.ahoo.cosky.rest.security.rbac.AuthorizeService;
import me.ahoo.cosky.rest.security.user.User;
import me.ahoo.cosky.rest.support.RequestPathPrefix;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
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
    private final AuthorizeService authorizeService;
    private final AuditLogService auditService;
    private final SecurityProperties securityProperties;

    public AuthorizeHandlerInterceptor(AuthorizeService authorizeService,
                                       AuditLogService auditService,
                                       SecurityProperties securityProperties) {
        this.authorizeService = authorizeService;
        this.auditService = auditService;
        this.securityProperties = securityProperties;
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
        return authorize(exchange)
                .flatMap(authorizeResult -> {
                    if (authorizeResult.isAuthorized()) {
                        return chain.filter(exchange);
                    }
                    exchange.getResponse().setStatusCode(authorizeResult.getStatus());
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
        if (requestPath.startsWith(RequestPathPrefix.AUTHENTICATE_PREFIX)) {
            String operator = requestPath.substring(RequestPathPrefix.AUTHENTICATE_PREFIX.length() + 1);
            int splitIdx = operator.indexOf("/");
            if (splitIdx > 0) {
                operator = operator.substring(0, splitIdx);
                auditLog.setOperator(operator);
            }
        } else {
            User currentUser = AuthorizeService.getRequiredUserOfRequest(exchange);
            auditLog.setOperator(currentUser.getUsername());
        }

//        HandlerMethod handlerMethod = exchange.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);

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

    public Mono<AuthorizeService.AuthorizeResult> authorize(ServerWebExchange exchange) {

        ServerHttpRequest request = exchange.getRequest();
        String requestPath = request.getPath().value();

        if (requestPath.startsWith(RequestPathPrefix.DASHBOARD)
                || requestPath.startsWith(RequestPathPrefix.SWAGGER_UI)
                || requestPath.startsWith("/actuator/health")
                || requestPath.startsWith("/v3/api-docs")
                || "/".equals(requestPath)
                || HttpMethod.OPTIONS.equals(request.getMethod())) {
            return Mono.just(AuthorizeService.AuthorizeResult.ALLOW_ANONYMOUS);
        }

        return authorizeService.authorize(exchange);
    }
}
