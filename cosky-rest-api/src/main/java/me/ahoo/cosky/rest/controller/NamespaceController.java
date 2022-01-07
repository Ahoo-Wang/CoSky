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

import me.ahoo.cosky.core.NamespaceService;
import me.ahoo.cosky.core.NamespacedContext;
import me.ahoo.cosky.rest.security.annotation.AdminResource;
import me.ahoo.cosky.rest.security.rbac.AuthorizeService;
import me.ahoo.cosky.rest.security.rbac.RBACService;
import me.ahoo.cosky.rest.security.user.User;
import me.ahoo.cosky.rest.support.RequestPathPrefix;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * @author ahoo wang
 */
@CrossOrigin("*")
@RestController
@RequestMapping(RequestPathPrefix.NAMESPACES_PREFIX)
public class NamespaceController {

    private final NamespaceService namespaceService;
    private final RBACService rbacService;

    public NamespaceController(NamespaceService namespaceService, RBACService rbacService) {
        this.namespaceService = namespaceService;
        this.rbacService = rbacService;
    }

    @GetMapping
    public Mono<Set<String>> getNamespaces(ServerWebExchange serverWebExchange) {
        User user = AuthorizeService.getRequiredUserOfRequest(serverWebExchange);
        if (user.isAdmin()) {
            return namespaceService.getNamespaces();
        }
        return rbacService.getCurrentUserNamespace(user);
    }

    @GetMapping(RequestPathPrefix.NAMESPACES_CURRENT)
    public String current() {
        return NamespacedContext.GLOBAL.getNamespace();
    }

    @AdminResource
    @PutMapping(RequestPathPrefix.NAMESPACES_CURRENT_NAMESPACE)
    public void setCurrentContextNamespace(@PathVariable String namespace) {
        NamespacedContext.GLOBAL.setCurrentContextNamespace(namespace);
    }

    @AdminResource
    @PutMapping(RequestPathPrefix.NAMESPACES_NAMESPACE)
    public Mono<Boolean> setNamespace(@PathVariable String namespace) {
        return namespaceService.setNamespace(namespace);
    }

    @AdminResource
    @DeleteMapping(RequestPathPrefix.NAMESPACES_NAMESPACE)
    public Mono<Boolean> removeNamespace(@PathVariable String namespace) {
        return namespaceService.removeNamespace(namespace);
    }
}
