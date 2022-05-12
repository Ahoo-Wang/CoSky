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

package me.ahoo.cosky.rest.controller;

import static me.ahoo.cosky.rest.support.RequestPathPrefix.ROLES_ROLE;
import static me.ahoo.cosky.rest.support.RequestPathPrefix.ROLES_ROLE_BIND;

import me.ahoo.cosky.rest.dto.role.ResourceActionDto;
import me.ahoo.cosky.rest.dto.role.RoleDto;
import me.ahoo.cosky.rest.dto.role.SaveRoleRequest;
import me.ahoo.cosky.rest.security.annotation.AdminResource;
import me.ahoo.cosky.rest.security.rbac.RbacService;
import me.ahoo.cosky.rest.support.RequestPathPrefix;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

/**
 * Role Controller.
 *
 * @author ahoo wang
 */
@CrossOrigin("*")
@RestController
@RequestMapping(RequestPathPrefix.ROLES_PREFIX)
@AdminResource
public class RoleController {
    private final RbacService rbacService;
    
    public RoleController(RbacService rbacService) {
        this.rbacService = rbacService;
    }
    
    @GetMapping
    public Mono<Set<RoleDto>> getAllRole() {
        return rbacService.getAllRole();
    }
    
    @GetMapping(ROLES_ROLE_BIND)
    public Mono<List<ResourceActionDto>> getResourceBind(@PathVariable String roleName) {
        return rbacService.getResourceBind(roleName)
            .map(resourceAction -> {
                ResourceActionDto dto = new ResourceActionDto();
                dto.setNamespace(resourceAction.getNamespace());
                dto.setAction(resourceAction.getAction().getValue());
                return dto;
            })
            .collectList();
    }
    
    @PutMapping(ROLES_ROLE)
    public Mono<Void> saveRole(@PathVariable String roleName, @RequestBody SaveRoleRequest saveRoleRequest) {
        return rbacService.saveRole(roleName, saveRoleRequest);
    }
    
    @DeleteMapping(ROLES_ROLE)
    public Mono<Boolean> removeRole(@PathVariable String roleName) {
        return rbacService.removeRole(roleName);
    }
}
