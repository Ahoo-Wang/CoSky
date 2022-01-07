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

package me.ahoo.cosky.rest.security.rbac;

import com.google.common.base.Strings;
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands;
import me.ahoo.cosky.core.Namespaced;
import me.ahoo.cosky.core.redis.RedisConnectionFactory;
import me.ahoo.cosky.rest.dto.role.RoleDto;
import me.ahoo.cosky.rest.dto.role.SaveRoleRequest;
import me.ahoo.cosky.rest.security.user.User;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

    private final RedisClusterReactiveCommands<String, String> redisCommands;

    public RBACService(RedisConnectionFactory redisConnectionFactory) {
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
}
