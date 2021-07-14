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
import com.google.common.collect.Sets;
import io.lettuce.core.cluster.api.sync.RedisClusterCommands;
import me.ahoo.cosky.core.Namespaced;
import me.ahoo.cosky.core.redis.RedisConnectionFactory;
import me.ahoo.cosky.rest.dto.role.ResourceActionDto;
import me.ahoo.cosky.rest.security.JwtProvider;
import me.ahoo.cosky.rest.security.SecurityContext;
import me.ahoo.cosky.rest.security.rbac.annotation.AdminResource;
import me.ahoo.cosky.rest.security.user.User;
import me.ahoo.cosky.rest.support.RequestPathPrefix;
import org.springframework.stereotype.Service;
import org.springframework.web.method.HandlerMethod;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;

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

    public void saveRole(String roleName, Set<ResourceActionDto> resourceActionBind) {
        redisCommands.sadd(ROLE_IDX, roleName);
        String roleResourceBindKey = getRoleResourceBindKey(roleName);
        redisCommands.del(roleResourceBindKey);
        for (ResourceActionDto resourceAction : resourceActionBind) {
            redisCommands.hset(roleResourceBindKey, resourceAction.getNamespace(), resourceAction.getAction());
        }
    }

    public void removeRole(String roleName) {
        redisCommands.srem(ROLE_IDX, roleName);
        String roleResourceBindKey = getRoleResourceBindKey(roleName);
        redisCommands.del(roleResourceBindKey);
    }

    public Set<String> getAllRole() {
        Set<String> roles = redisCommands.smembers(ROLE_IDX);
        Set<String> allRole = Sets.newHashSet(roles);
        allRole.add(Role.ADMIN_ROLE);
        return allRole;
    }

    public Role getRole(String roleName) throws NotFoundRoleException {
        String roleResourceBindKey = getRoleResourceBindKey(roleName);
        Map<String, String> roleResourceBindMap = redisCommands.hgetall(roleResourceBindKey);
        if (roleResourceBindMap == null) {
            throw new NotFoundRoleException(roleName);
        }
        Role role = new Role();
        role.setRoleName(roleName);
        roleResourceBindMap.forEach((namespace, action) -> {
            ResourceAction resourceAction = new ResourceAction(namespace, Action.of(action));
            role.getResourceActionBind().put(namespace, resourceAction);
        });
        return role;
    }

    /**
     * 权限控制
     */
    public boolean authorize(String accessToken, HttpServletRequest request, HandlerMethod handlerMethod) {

        User user = jwtProvider.authorize(accessToken);
        SecurityContext.setUser(user);
        if (User.SUPER_USER.equals(user.getUsername())) {
            return true;
        }

        String requestUrl = request.getRequestURI();
        if (RequestPathPrefix.NAMESPACES_PREFIX.equals(requestUrl)) {
            return true;
        }

        boolean isAdminResource = handlerMethod.hasMethodAnnotation(AdminResource.class);
        if (isAdminResource && !user.isAdmin()) {
            return true;
        }

        String namespace = requestUrl.substring(RequestPathPrefix.NAMESPACES_PREFIX.length() + 1);
        int splitIdx = namespace.indexOf("/");
        if (splitIdx > 0) {
            namespace = namespace.substring(0, splitIdx);
        }

        ResourceAction requestAction = new ResourceAction(namespace, Action.ofHttpMethod(request.getMethod()));
        return authorize(user, requestAction);
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
