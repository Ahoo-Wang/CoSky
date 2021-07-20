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

package me.ahoo.cosky.rest.controller;

import me.ahoo.cosky.rest.dto.role.ResourceActionDto;
import me.ahoo.cosky.rest.dto.role.RoleDto;
import me.ahoo.cosky.rest.dto.role.SaveRoleRequest;
import me.ahoo.cosky.rest.security.rbac.RBACService;
import me.ahoo.cosky.rest.security.annotation.AdminResource;
import me.ahoo.cosky.rest.support.RequestPathPrefix;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.stream.Collectors;

import static me.ahoo.cosky.rest.support.RequestPathPrefix.*;

/**
 * @author ahoo wang
 */
@CrossOrigin("*")
@RestController
@RequestMapping(RequestPathPrefix.ROLES_PREFIX)
@AdminResource
public class RoleController {
    private final RBACService rbacService;

    public RoleController(RBACService rbacService) {
        this.rbacService = rbacService;
    }

    @GetMapping
    public Set<RoleDto> getAllRole() {
        return rbacService.getAllRole();
    }

    @GetMapping(ROLES_ROLE_BIND)
    public Set<ResourceActionDto> getResourceBind(@PathVariable String roleName) {
        return rbacService.getResourceBind(roleName)
                .stream()
                .map(resourceAction -> {
                    ResourceActionDto dto = new ResourceActionDto();
                    dto.setNamespace(resourceAction.getNamespace());
                    dto.setAction(resourceAction.getAction().getValue());
                    return dto;
                }).collect(Collectors.toSet());
    }

    @PutMapping(ROLES_ROLE)
    public void saveRole(@PathVariable String roleName, @RequestBody SaveRoleRequest saveRoleRequest) {
        rbacService.saveRole(roleName, saveRoleRequest);
    }

    @DeleteMapping(ROLES_ROLE)
    public void removeRole(@PathVariable String roleName) {
        rbacService.removeRole(roleName);
    }
}
