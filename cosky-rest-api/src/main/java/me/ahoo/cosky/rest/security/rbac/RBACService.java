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
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands;
import me.ahoo.cosky.core.Namespaced;
import me.ahoo.cosky.core.redis.RedisConnectionFactory;
import me.ahoo.cosky.rest.dto.role.RoleDto;
import me.ahoo.cosky.rest.dto.role.SaveRoleRequest;
import me.ahoo.cosky.rest.security.JwtProvider;
import me.ahoo.cosky.rest.security.TokenExpiredException;
import me.ahoo.cosky.rest.security.annotation.AdminResource;
import me.ahoo.cosky.rest.security.annotation.OwnerResource;
import me.ahoo.cosky.rest.security.user.User;
import me.ahoo.cosky.rest.support.RequestPathPrefix;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author ahoo wang
 */
@Service
public class RBACService {

    /**
     * set
     */
    public static final String ROLE_IDX = Namespaced.SYSTEM + ":role_idx";
    /**
     * hash
     */
    public static final String ROLE_RESOURCE_BIND = Namespaced.SYSTEM + ":role_resource_bind:%s";
    private final JwtProvider jwtProvider;
    private final RedisClusterReactiveCommands<String, String> redisCommands;

    public RBACService(JwtProvider jwtProvider, RedisConnectionFactory redisConnectionFactory) {
        this.jwtProvider = jwtProvider;
        this.redisCommands = redisConnectionFactory.getShareReactiveCommands();
    }

    private String getRoleResourceBindKey(String roleName) {
        return Strings.lenientFormat(ROLE_RESOURCE_BIND, roleName);
    }

    public Mono<Void> saveRole(String roleName, SaveRoleRequest saveRoleRequest) {
        String roleResourceBindKey = getRoleResourceBindKey(roleName);
        return redisCommands.hset(ROLE_IDX, roleName, saveRoleRequest.getDesc())
                .flatMap(nil -> redisCommands.del(roleResourceBindKey))
                .thenMany(Flux.fromIterable(saveRoleRequest.getResourceActionBind()))
                .flatMap(resourceAction -> redisCommands.hset(roleResourceBindKey, resourceAction.getNamespace(), resourceAction.getAction()))
                .then();
    }

    public Mono<Boolean> removeRole(String roleName) {
        String roleResourceBindKey = getRoleResourceBindKey(roleName);
        return redisCommands.hdel(ROLE_IDX, roleName)
                .then(redisCommands.del(roleResourceBindKey))
                .map(affected -> affected > 0);
    }

    public Mono<Set<RoleDto>> getAllRole() {
        return redisCommands.hgetall(ROLE_IDX)
                .map(entry -> {
                    RoleDto dto = new RoleDto();
                    dto.setName(entry.getKey());
                    dto.setDesc(entry.getValue());
                    return dto;
                })
                .collect(Collectors.toSet())
                .doOnNext(roles -> roles.add(RoleDto.ADMIN));
    }

    public Mono<Role> getRole(String roleName) throws NotFoundRoleException {
        return redisCommands.hget(ROLE_IDX, roleName)
                .switchIfEmpty(Mono.error(new NotFoundRoleException(roleName)))
                .flatMap(roleDesc -> getResourceBind(roleName).collect(Collectors.toSet())
                        .map(resourceActions -> {
                            Role role = new Role();
                            role.setRoleName(roleName);
                            role.setDesc(roleDesc);
                            resourceActions.forEach(resourceAction -> {
                                role.getResourceActionBind().put(resourceAction.getNamespace(), resourceAction);
                            });
                            return role;
                        }));
    }

    public Flux<ResourceAction> getResourceBind(String roleName) {
        String roleResourceBindKey = getRoleResourceBindKey(roleName);
        return redisCommands.hgetall(roleResourceBindKey)
                .switchIfEmpty(Mono.error(new NotFoundRoleException(roleName)))
                .map(entry -> new ResourceAction(entry.getKey(), Action.of(entry.getValue())));
    }


    public Mono<Set<String>> getCurrentUserNamespace(User user) {
        Set<String> userRoleBind = user.getRoleBind();
        return Flux.fromIterable(userRoleBind)
                .flatMap(this::getRole)
                .flatMapIterable(role -> role.getResourceActionBind().keySet())
                .collect(Collectors.toSet());
    }

    public static final String CURRENT_USER_KEY = "cosky.currentUser";

    /**
     * 权限控制
     */
    public Mono<Boolean> authorize(String accessToken, ServerWebExchange serverWebExchange, Mono<HandlerMethod> handlerMethodMono) throws TokenExpiredException {
        User user;
        try {
            user = jwtProvider.authorize(accessToken);
        } catch (ExpiredJwtException expiredJwtException) {
            throw new TokenExpiredException(expiredJwtException);
        }
        ServerHttpRequest request = serverWebExchange.getRequest();

        serverWebExchange.getAttributes().put(CURRENT_USER_KEY, user);

        if (User.SUPER_USER.equals(user.getUsername()) || user.isAdmin()) {
            return Mono.just(true);
        }

        final String requestUrl = request.getPath().value();
        if (RequestPathPrefix.NAMESPACES_PREFIX.equals(requestUrl)) {
            return Mono.just(true);
        }
        return handlerMethodMono
                .flatMap(handlerMethod -> {
                    boolean isOwnerResource = AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), OwnerResource.class)
                            || handlerMethod.hasMethodAnnotation(OwnerResource.class);
                    if (isOwnerResource) {
                        return Mono.just(true);
                    }

                    boolean isAdminResource = AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), AdminResource.class)
                            || handlerMethod.hasMethodAnnotation(AdminResource.class);
                    if (isAdminResource) {
                        return Mono.just(false);
                    }

                    String namespace = requestUrl.substring(RequestPathPrefix.NAMESPACES_PREFIX.length() + 1);
                    int splitIdx = namespace.indexOf("/");
                    if (splitIdx > 0) {
                        namespace = namespace.substring(0, splitIdx);
                    }

                    ResourceAction requestAction = new ResourceAction(namespace, Action.ofHttpMethod(Objects.requireNonNull(request.getMethod())));
                    return authorize(user, requestAction);
                })
                .defaultIfEmpty(Boolean.FALSE);
    }

    public static User getUserOfRequest(ServerWebExchange serverWebExchange) {
        Object user = serverWebExchange.getAttribute(CURRENT_USER_KEY);
        if (Objects.isNull(user)) {
            return null;
        }
        return (User) user;
    }

    /**
     * 权限控制
     */
    public Mono<Boolean> authorize(User user, ResourceAction requestAction) {
        return Flux.fromIterable(user.getRoleBind())
                .flatMap(this::getRole)
                .any(role -> role.check(requestAction));
    }
}
