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
package me.ahoo.cosky.rest.security.authorization

import me.ahoo.cosec.api.authorization.Authorization
import me.ahoo.cosec.api.authorization.AuthorizeResult
import me.ahoo.cosec.api.context.SecurityContext
import me.ahoo.cosec.api.context.request.Request
import me.ahoo.cosec.api.policy.VerifyResult
import me.ahoo.cosec.api.principal.CoSecPrincipal.Companion.isRoot
import me.ahoo.cosky.rest.security.authorization.NamespaceRequestAttributesAppender.getNamespace
import me.ahoo.cosky.rest.security.rbac.Action.Companion.httpMethodAsAction
import me.ahoo.cosky.rest.security.rbac.RbacService
import me.ahoo.cosky.rest.security.rbac.ResourceAction
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

@Service
class CoSkyAuthorization(
    private val rbacService: RbacService,
    private val coSkyPolicy: CoSkyPolicy
) : Authorization {

    override fun authorize(request: Request, context: SecurityContext): Mono<AuthorizeResult> {
        if (context.principal.isRoot) {
            return AuthorizeResult.ALLOW.toMono()
        }
        return coSkyPolicy.getPolicy().flatMap { policy ->
            val verifyResult = policy.verify(request, context)
            if (verifyResult == VerifyResult.ALLOW) {
                return@flatMap AuthorizeResult.ALLOW.toMono()
            }
            if (verifyResult == VerifyResult.EXPLICIT_DENY) {
                return@flatMap AuthorizeResult.EXPLICIT_DENY.toMono()
            }

            val namespace = request.getNamespace() ?: return@flatMap AuthorizeResult.IMPLICIT_DENY.toMono()

            val requestAction = ResourceAction(namespace, request.method.httpMethodAsAction())
            return@flatMap checkRolePermissions(context.principal.roles, requestAction)
                .map {
                    if (it) {
                        return@map AuthorizeResult.ALLOW
                    }
                    AuthorizeResult.IMPLICIT_DENY
                }
        }
    }

    private fun checkRolePermissions(roleBind: Set<String>, requestAction: ResourceAction): Mono<Boolean> {
        return rbacService.getRoles(roleBind)
            .any {
                it.check(
                    requestAction,
                )
            }
    }
}
