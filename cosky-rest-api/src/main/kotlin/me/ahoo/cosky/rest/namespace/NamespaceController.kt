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
package me.ahoo.cosky.rest.namespace

import me.ahoo.cosec.webflux.ReactiveSecurityContexts.getSecurityContext
import me.ahoo.cosky.core.NamespaceService
import me.ahoo.cosky.core.NamespacedContext
import me.ahoo.cosky.core.NamespacedContext.namespace
import me.ahoo.cosky.rest.security.rbac.RbacService
import me.ahoo.cosky.rest.security.user.AdminPrincipal.isAdmin
import me.ahoo.cosky.rest.support.RequestPathPrefix
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * Namespace Controller.
 *
 * @author ahoo wang
 */
@CrossOrigin("*")
@RestController
@RequestMapping(RequestPathPrefix.NAMESPACES_PREFIX)
class NamespaceController(private val namespaceService: NamespaceService, private val rbacService: RbacService) {
    @GetMapping
    fun getNamespaces(): Mono<List<String>> {
        return Mono.deferContextual {
            val principal = it.getSecurityContext().principal
            if (principal.isAdmin) {
                return@deferContextual namespaceService.namespaces.collectList()
            }
            rbacService.getCurrentUserNamespace(principal).collectList()
        }
    }

    @GetMapping(RequestPathPrefix.NAMESPACES_CURRENT)
    fun current(): String {
        return namespace
    }

    @PutMapping(RequestPathPrefix.NAMESPACES_CURRENT_NAMESPACE)
    fun setCurrentContextNamespace(@PathVariable namespace: String) {
        NamespacedContext.namespace = namespace
    }

    @PutMapping(RequestPathPrefix.NAMESPACES_NAMESPACE)
    fun setNamespace(@PathVariable namespace: String): Mono<Boolean> {
        return namespaceService.setNamespace(namespace)
    }

    @DeleteMapping(RequestPathPrefix.NAMESPACES_NAMESPACE)
    fun removeNamespace(@PathVariable namespace: String): Mono<Boolean> {
        return namespaceService.removeNamespace(namespace)
    }
}
