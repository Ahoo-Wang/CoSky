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

import io.github.oshai.kotlinlogging.KotlinLogging
import me.ahoo.cosky.core.Namespaced
import me.ahoo.cosky.core.cache.ReactiveWatchCache
import me.ahoo.cosky.core.cache.WatchCache
import me.ahoo.cosky.core.redis.LuaScripts
import me.ahoo.cosky.rest.security.rbac.Action.Companion.asAction
import org.springframework.beans.factory.DisposableBean
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.stream.Collectors

/**
 * RBAC Service.
 *
 * Roles are read on every authorized request, so [getRole] is served from a local cache that role changes,
 * published by the role scripts, keep consistent across every server instance.
 *
 * @author ahoo wang
 */
@Service
class RbacService(
    private val redisTemplate: ReactiveStringRedisTemplate,
    listenerContainer: ReactiveRedisMessageListenerContainer
) : DisposableBean {
    private val roleCache: WatchCache<String, Role> = ReactiveWatchCache(
        listen = { roleName ->
            listenerContainer.receiveLater(ChannelTopic.of(getRoleResourceBindKey(roleName)))
                .map { messages -> messages.map { it.message } }
        },
        load = { loadRole(it) },
    )

    private fun getRoleResourceBindKey(roleName: String): String {
        return ROLE_RESOURCE_BIND.format(roleName)
    }

    fun saveRole(roleName: String, saveRoleRequest: SaveRoleRequest): Mono<Void> {
        val args = buildList(2 + saveRoleRequest.resourceActionBind.size * 2) {
            add(roleName)
            add(saveRoleRequest.desc)
            saveRoleRequest.resourceActionBind.forEach { (namespace, action) ->
                add(namespace)
                add(action)
            }
        }
        return redisTemplate.execute(
            SCRIPT_SAVE_ROLE,
            listOf(ROLE_IDX, getRoleResourceBindKey(roleName)),
            args,
        ).then()
    }

    fun removeRole(roleName: String): Mono<Boolean> {
        return redisTemplate.execute(
            SCRIPT_REMOVE_ROLE,
            listOf(ROLE_IDX, getRoleResourceBindKey(roleName)),
            listOf(roleName),
        ).next().map { it > 0 }
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
        return roleCache.get(roleName).switchIfEmpty(Mono.error { NotFoundRoleException(roleName) })
    }

    private fun loadRole(roleName: String): Mono<Role> {
        return redisTemplate
            .opsForHash<String, String>()[ROLE_IDX, roleName]
            .flatMap { roleDesc ->
                redisTemplate
                    .opsForHash<String, String>()
                    .entries(getRoleResourceBindKey(roleName))
                    .map { (namespace, action) -> ResourceAction(namespace, action.asAction()) }
                    .collectList()
                    .map { resourceActions -> Role(roleName, roleDesc, resourceActions.associateBy { it.namespace }) }
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

    /**
     * The existing roles among [roleNames].
     *
     * A missing role grants nothing but never fails the others: removing a role neither unbinds its users nor
     * revokes the tokens that carry it.
     */
    fun getRoles(roleNames: Set<String>): Flux<Role> {
        return Flux.fromIterable(roleNames)
            .flatMap { roleName ->
                getRole(roleName).onErrorResume(NotFoundRoleException::class.java) {
                    log.debug { "Role[$roleName] is not found, granting nothing." }
                    Mono.empty()
                }
            }
    }

    fun getRoleNamespaces(roles: Set<String>): Flux<String> {
        return getRoles(roles)
            .flatMapIterable { (_, _, resourceActionBind) -> resourceActionBind.keys }
            .distinct()
    }

    companion object {
        private val log = KotlinLogging.logger {}

        /**
         * set.
         */
        const val ROLE_IDX = Namespaced.SYSTEM + ":role_idx"

        /**
         * hash.
         */
        const val ROLE_RESOURCE_BIND = Namespaced.SYSTEM + ":role_resource_bind:%s"
        private val SCRIPT_SAVE_ROLE: RedisScript<Long> = LuaScripts.load("rbac_save_role.lua", Long::class.java)
        private val SCRIPT_REMOVE_ROLE: RedisScript<Long> = LuaScripts.load("rbac_remove_role.lua", Long::class.java)
    }

    override fun destroy() {
        roleCache.close()
    }
}
