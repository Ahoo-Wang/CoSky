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

package me.ahoo.cosky.rest.security.rbac;

import com.google.common.base.Strings;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosky.rest.security.CoSkySecurityException;
import me.ahoo.cosky.rest.security.JwtProvider;
import me.ahoo.cosky.rest.security.TokenExpiredException;
import me.ahoo.cosky.rest.security.annotation.AdminResource;
import me.ahoo.cosky.rest.security.annotation.AllowAnonymous;
import me.ahoo.cosky.rest.security.annotation.OwnerResource;
import me.ahoo.cosky.rest.security.user.User;
import me.ahoo.cosky.rest.support.RequestPathPrefix;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.Optional;

/**
 * @author ahoo wang
 */
@Slf4j
@Service
public class AuthorizeService {
    public static final String CURRENT_USER_KEY = "cosky.currentUser";
    public static final String AUTH_HEADER = "Authorization";
    public static final String QUERY_PARAM_TOKEN = "token";

    private final RBACService rbacService;
    private final JwtProvider jwtProvider;
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    public AuthorizeService(RBACService rbacService, JwtProvider jwtProvider, RequestMappingHandlerMapping requestMappingHandlerMapping) {
        this.rbacService = rbacService;
        this.jwtProvider = jwtProvider;
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
    }

    public static Optional<String> getToken(ServerHttpRequest request) {
        final String headerToken = request.getHeaders().getFirst(AUTH_HEADER);
        if (!Strings.isNullOrEmpty(headerToken)) {
            return Optional.of(headerToken);
        }
        final String queryToken = request.getQueryParams().getFirst(QUERY_PARAM_TOKEN);
        if (!Strings.isNullOrEmpty(queryToken)) {
            return Optional.of(queryToken);
        }
        return Optional.empty();
    }

    public Mono<AuthorizeResult> authorize(ServerWebExchange exchange) throws TokenExpiredException {
        return requestMappingHandlerMapping
                .getHandler(exchange)
                .ofType(HandlerMethod.class)
                .flatMap(handlerMethod -> authorize(exchange, handlerMethod))
                .defaultIfEmpty(AuthorizeResult.UNAUTHORIZED);
    }

    private Mono<AuthorizeResult> authorize(ServerWebExchange exchange, HandlerMethod handlerMethod) {
        ServerHttpRequest request = exchange.getRequest();
        String requestPath = request.getPath().value();

        final Optional<String> accessToken = getToken(request);
        if (hasAnnotation(handlerMethod, AllowAnonymous.class)) {
            return Mono.just(AuthorizeResult.ALLOW_ANONYMOUS);
        }

        if (!accessToken.isPresent()) {
            return Mono.just(AuthorizeResult.UNAUTHORIZED);
        }

        final User user;
        try {
            user = jwtProvider.authorize(accessToken.get());
            exchange.getAttributes().put(CURRENT_USER_KEY, user);
        } catch (ExpiredJwtException expiredJwtException) {
            if (log.isInfoEnabled()) {
                log.info(expiredJwtException.getMessage(), expiredJwtException);
            }
            return Mono.just(AuthorizeResult.UNAUTHORIZED);
        }

        if (hasAnnotation(handlerMethod, AllowAnonymous.class)) {
            return Mono.just(AuthorizeResult.ofAuthorized(user));
        }

        if (user.isAdmin()) {
            return Mono.just(AuthorizeResult.ofAuthorized(user));
        }

        if (hasAnnotation(handlerMethod, OwnerResource.class)) {
            return Mono.just(AuthorizeResult.ofAuthorized(user));
        }

        if (hasAnnotation(handlerMethod, AdminResource.class)) {
            return Mono.just(AuthorizeResult.ofForbidden(user));
        }

        final String namespace = resolveNamespace(requestPath);
        ResourceAction requestAction = new ResourceAction(namespace, Action.ofHttpMethod(Objects.requireNonNull(request.getMethod())));
        return checkRolePermissions(user, requestAction)
                .map(result -> {
                    if (result) {
                        return AuthorizeResult.ofAuthorized(user);
                    }
                    return AuthorizeResult.ofForbidden(user);
                });
    }

    private String resolveNamespace(String requestPath) {
        String namespace = requestPath.substring(RequestPathPrefix.NAMESPACES_PREFIX.length() + 1);
        int splitIdx = namespace.indexOf("/");
        if (splitIdx > 0) {
            return namespace.substring(0, splitIdx);
        }
        return namespace;
    }


    public static Optional<User> getUserOfRequest(ServerWebExchange serverWebExchange) {
        User user = serverWebExchange.getAttribute(CURRENT_USER_KEY);
        return Optional.ofNullable(user);
    }

    public static User getRequiredUserOfRequest(ServerWebExchange serverWebExchange) {
        return getUserOfRequest(serverWebExchange)
                .orElseThrow(() -> new CoSkySecurityException("UNAUTHORIZED"));
    }

    public static boolean hasAnnotation(HandlerMethod handlerMethod, Class<? extends Annotation> annotationType) {
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), annotationType)
                || handlerMethod.hasMethodAnnotation(annotationType);
    }

    public Mono<Boolean> checkRolePermissions(User user, ResourceAction requestAction) {
        return Flux.fromIterable(user.getRoleBind())
                .flatMap(rbacService::getRole)
                .any(role -> role.check(requestAction));
    }

    public static class AuthorizeResult {

        public static final AuthorizeResult ALLOW_ANONYMOUS = new AuthorizeResult(true, HttpStatus.OK, null);
        public static final AuthorizeResult UNAUTHORIZED = new AuthorizeResult(false, HttpStatus.UNAUTHORIZED, null);
        private final boolean authorized;
        private final HttpStatus status;
        private final User identity;

        public static AuthorizeResult ofAuthorized(User identity) {
            return new AuthorizeResult(true, HttpStatus.OK, identity);
        }

        public static AuthorizeResult ofForbidden(User identity) {
            return new AuthorizeResult(false, HttpStatus.FORBIDDEN, identity);
        }

        public AuthorizeResult(boolean authorized, HttpStatus status, User identity) {
            this.authorized = authorized;
            this.status = status;
            this.identity = identity;
        }

        public boolean isAuthorized() {
            return authorized;
        }

        public HttpStatus getStatus() {
            return status;
        }

        public User getIdentity() {
            return identity;
        }
    }


}
