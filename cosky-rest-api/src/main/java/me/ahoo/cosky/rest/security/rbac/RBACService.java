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

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import io.jsonwebtoken.ExpiredJwtException;
import io.lettuce.core.cluster.api.sync.RedisClusterCommands;
import lombok.SneakyThrows;
import me.ahoo.cosky.core.Namespaced;
import me.ahoo.cosky.core.redis.RedisConnectionFactory;
import me.ahoo.cosky.rest.dto.role.ResourceActionDto;
import me.ahoo.cosky.rest.dto.role.RoleDto;
import me.ahoo.cosky.rest.dto.role.SaveRoleRequest;
import me.ahoo.cosky.rest.security.JwtProvider;
import me.ahoo.cosky.rest.security.SecurityContext;
import me.ahoo.cosky.rest.security.TokenExpiredException;
import me.ahoo.cosky.rest.security.annotation.AdminResource;
import me.ahoo.cosky.rest.security.annotation.OwnerResource;
import me.ahoo.cosky.rest.security.user.User;
import me.ahoo.cosky.rest.support.RequestPathPrefix;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.method.HandlerMethod;

import javax.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.Map;
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
    private final RedisClusterCommands<String, String> redisCommands;

    public RBACService(JwtProvider jwtProvider, RedisConnectionFactory redisConnectionFactory) {
        this.jwtProvider = jwtProvider;
        this.redisCommands = redisConnectionFactory.getShareSyncCommands();
    }

    private String getRoleResourceBindKey(String roleName) {
        return Strings.lenientFormat(ROLE_RESOURCE_BIND, roleName);
    }

    public void saveRole(String roleName, SaveRoleRequest saveRoleRequest) {
        redisCommands.hset(ROLE_IDX, roleName, saveRoleRequest.getDesc());
        String roleResourceBindKey = getRoleResourceBindKey(roleName);
        redisCommands.del(roleResourceBindKey);
        for (ResourceActionDto resourceAction : saveRoleRequest.getResourceActionBind()) {
            redisCommands.hset(roleResourceBindKey, resourceAction.getNamespace(), resourceAction.getAction());
        }
    }

    public void removeRole(String roleName) {
        redisCommands.hdel(ROLE_IDX, roleName);
        String roleResourceBindKey = getRoleResourceBindKey(roleName);
        redisCommands.del(roleResourceBindKey);
    }

    public Set<RoleDto> getAllRole() {
        Map<String, String> roleMap = redisCommands.hgetall(ROLE_IDX);
        Set<RoleDto> allRole = Sets.newHashSet();
        for (Map.Entry<String, String> entry : roleMap.entrySet()) {
            RoleDto dto = new RoleDto();
            dto.setName(entry.getKey());
            dto.setDesc(entry.getValue());
            allRole.add(dto);
        }
        allRole.add(RoleDto.ADMIN);
        return allRole;
    }

    public Role getRole(String roleName) throws NotFoundRoleException {
        String roleDesc = redisCommands.hget(ROLE_IDX, roleName);
        if (roleDesc == null) {
            throw new NotFoundRoleException(roleName);
        }
        Role role = new Role();
        role.setRoleName(roleName);
        role.setDesc(roleDesc);
        Set<ResourceAction> resourceActionBind = getResourceBind(roleName);
        for (ResourceAction resourceAction : resourceActionBind) {
            role.getResourceActionBind().put(resourceAction.getNamespace(), resourceAction);
        }
        return role;
    }

    public Set<ResourceAction> getResourceBind(String roleName) {
        String roleResourceBindKey = getRoleResourceBindKey(roleName);
        Map<String, String> roleResourceBindMap = redisCommands.hgetall(roleResourceBindKey);
        if (roleResourceBindMap == null) {
            throw new NotFoundRoleException(roleName);
        }
        Set<ResourceAction> resourceActionBind = Sets.newHashSet();
        for (Map.Entry<String, String> entry : roleResourceBindMap.entrySet()) {
            ResourceAction resourceAction = new ResourceAction(entry.getKey(), Action.of(entry.getValue()));
            resourceActionBind.add(resourceAction);
        }
        return resourceActionBind;

    }


    public Set<String> getCurrentUserNamespace() {
        if (!SecurityContext.authorized()) {
            return Collections.emptySet();
        }

        Set<String> userRoleBind = SecurityContext.getUser().getRoleBind();
        return userRoleBind.stream()
                .flatMap(role -> getRole(role).getResourceActionBind().keySet().stream())
                .collect(Collectors.toSet());
    }

    public static final String CURRENT_USER_KEY = "cosky.currentUser";

    /**
     * 权限控制
     */
    @SneakyThrows
    public boolean authorize(String accessToken, HttpServletRequest request, HandlerMethod handlerMethod) throws TokenExpiredException {
        User user;
        try {
            user = jwtProvider.authorize(accessToken);
        } catch (ExpiredJwtException expiredJwtException) {
            throw new TokenExpiredException(expiredJwtException);
        }

        SecurityContext.setUser(user);
        request.setAttribute(CURRENT_USER_KEY, SecurityContext.getUser());

        if (User.SUPER_USER.equals(user.getUsername()) || user.isAdmin()) {
            return true;
        }

        String requestUrl = request.getRequestURI();
        requestUrl = URLDecoder.decode(requestUrl, Charsets.UTF_8.name());
        if (RequestPathPrefix.NAMESPACES_PREFIX.equals(requestUrl)) {
            return true;
        }

        boolean isOwnerResource = AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), OwnerResource.class)
                || handlerMethod.hasMethodAnnotation(OwnerResource.class);
        if (isOwnerResource) {
            return true;
        }

        boolean isAdminResource = AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), AdminResource.class)
                || handlerMethod.hasMethodAnnotation(AdminResource.class);
        if (isAdminResource) {
            return false;
        }

        String namespace = requestUrl.substring(RequestPathPrefix.NAMESPACES_PREFIX.length() + 1);
        int splitIdx = namespace.indexOf("/");
        if (splitIdx > 0) {
            namespace = namespace.substring(0, splitIdx);
        }

        ResourceAction requestAction = new ResourceAction(namespace, Action.ofHttpMethod(request.getMethod()));
        return authorize(user, requestAction);
    }

    public User getUserOfRequest(HttpServletRequest request) {
        Object user = request.getAttribute(CURRENT_USER_KEY);
        if (Objects.isNull(user)) {
            return null;
        }
        return (User) user;
    }

    /**
     * 权限控制
     */
    public boolean authorize(User user, ResourceAction requestAction) {
        return user.getRoleBind()
                .stream()
                .map(roleName -> getRole(roleName))
                .anyMatch(role -> role.check(requestAction));
    }
}
