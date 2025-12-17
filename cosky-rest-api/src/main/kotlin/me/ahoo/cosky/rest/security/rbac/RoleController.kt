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

import io.swagger.v3.oas.annotations.tags.Tag
import me.ahoo.cosky.rest.support.RequestPathPrefix
import me.ahoo.cosky.rest.support.RequestPathPrefix.ROLES_ROLE
import me.ahoo.cosky.rest.support.RequestPathPrefix.ROLES_ROLE_BIND
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * Role Controller.
 *
 * @author ahoo wang
 */
@RestController
@RequestMapping(RequestPathPrefix.ROLES_PREFIX)
@Tag(name = "Role")
class RoleController(private val rbacService: RbacService) {
    @GetMapping
    fun allRole(): Mono<Set<RoleDto>> {
        return rbacService.allRole
    }

    @GetMapping(ROLES_ROLE_BIND)
    fun getResourceBind(@PathVariable roleName: String): Mono<List<ResourceActionDto>> {
        return rbacService.getResourceBind(roleName)
            .map {
                ResourceActionDto(namespace = it.namespace, action = it.action.value)
            }
            .collectList()
    }

    @PutMapping(ROLES_ROLE)
    fun saveRole(@PathVariable roleName: String, @RequestBody saveRoleRequest: SaveRoleRequest): Mono<Void> {
        return rbacService.saveRole(roleName, saveRoleRequest)
    }

    @DeleteMapping(ROLES_ROLE)
    fun removeRole(@PathVariable roleName: String): Mono<Boolean> {
        return rbacService.removeRole(roleName)
    }
}
