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

import com.google.common.base.Strings
import me.ahoo.cosky.core.Namespaced
import me.ahoo.cosky.rest.security.rbac.Action.Companion.asAction
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.stream.Collectors

/**
 * RBAC Service.
 *
 * @author ahoo wang
 */
@Service
class RbacService(private val redisTemplate: ReactiveStringRedisTemplate) {
    private fun getRoleResourceBindKey(roleName: String): String {
        return Strings.lenientFormat(ROLE_RESOURCE_BIND, roleName)
    }

    fun saveRole(roleName: String, saveRoleRequest: SaveRoleRequest): Mono<Void> {
        val roleResourceBindKey = getRoleResourceBindKey(roleName)
        return redisTemplate
            .opsForHash<Any, Any>()
            .put(ROLE_IDX, roleName, saveRoleRequest.desc)
            .flatMap { redisTemplate.delete(roleResourceBindKey) }
            .thenMany(Flux.fromIterable(saveRoleRequest.resourceActionBind))
            .flatMap { (namespace, action) ->
                redisTemplate.opsForHash<Any, Any>().put(roleResourceBindKey, namespace, action)
            }
            .then()
    }

    fun removeRole(roleName: String): Mono<Boolean> {
        val roleResourceBindKey = getRoleResourceBindKey(roleName)
        return redisTemplate
            .opsForHash<Any, Any>()
            .remove(ROLE_IDX, roleName)
            .then(redisTemplate.delete(roleResourceBindKey))
            .map { affected -> affected > 0 }
    }

    val allRole: Mono<Set<RoleDto>>
        get() = redisTemplate
            .opsForHash<String, String>()
            .entries(ROLE_IDX)
            .map { (key, value) ->
                RoleDto(key, value)
            }
            .collect(Collectors.toSet())
            .doOnNext { roles -> roles.add(RoleDto.ADMIN) }

    @Throws(NotFoundRoleException::class)
    fun getRole(roleName: String): Mono<Role> {
        return redisTemplate
            .opsForHash<String, String>()[ROLE_IDX, roleName]
            .switchIfEmpty(Mono.error(NotFoundRoleException(roleName)))
            .flatMap { roleDesc ->
                getResourceBind(roleName).collect(Collectors.toSet())
                    .map { resourceActions: Set<ResourceAction> ->
                        val resourceActionBind = resourceActions
                            .associateBy { resourceAction ->
                                resourceAction.namespace
                            }
                        Role(roleName, roleDesc, resourceActionBind)
                    }
            }
    }

    fun getResourceBind(roleName: String): Flux<ResourceAction> {
        val roleResourceBindKey = getRoleResourceBindKey(roleName)
        return redisTemplate
            .opsForHash<String, String>()
            .entries(roleResourceBindKey)
            .switchIfEmpty(Mono.error(NotFoundRoleException(roleName)))
            .map { (key, value) ->
                ResourceAction(
                    key,
                    value.asAction(),
                )
            }
    }

    fun getRoleNamespaces(roles: Set<String>): Flux<String> {
        return Flux.fromIterable(roles)
            .flatMap { roleName -> getRole(roleName) }
            .flatMapIterable { (_, _, resourceActionBind) -> resourceActionBind.keys }
    }

    companion object {
        /**
         * set.
         */
        const val ROLE_IDX = Namespaced.SYSTEM + ":role_idx"

        /**
         * hash.
         */
        const val ROLE_RESOURCE_BIND = Namespaced.SYSTEM + ":role_resource_bind:%s"
    }
}
