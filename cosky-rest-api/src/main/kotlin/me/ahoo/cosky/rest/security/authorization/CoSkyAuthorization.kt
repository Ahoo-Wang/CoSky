package me.ahoo.cosky.rest.security.authorization

import me.ahoo.cosec.api.authorization.Authorization
import me.ahoo.cosec.api.authorization.AuthorizeResult
import me.ahoo.cosec.api.context.SecurityContext
import me.ahoo.cosec.api.context.request.Request
import me.ahoo.cosec.api.policy.Policy
import me.ahoo.cosec.api.policy.VerifyResult
import me.ahoo.cosec.api.principal.CoSecPrincipal.Companion.isRoot
import me.ahoo.cosec.policy.DefaultPolicyEvaluator
import me.ahoo.cosec.serialization.CoSecJsonSerializer
import me.ahoo.cosky.rest.security.rbac.Action.Companion.httpMethodAsAction
import me.ahoo.cosky.rest.security.authorization.NamespaceRequestAttributesAppender.getNamespace
import me.ahoo.cosky.rest.security.rbac.RbacService
import me.ahoo.cosky.rest.security.rbac.ResourceAction
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

@Service
class CoSkyAuthorization(private val rbacService: RbacService) : Authorization {
    private val policy: Policy by lazy {
        requireNotNull(javaClass.classLoader.getResource("cosky-policy.json")).let { resource ->
            resource.openStream().use {
                val policy = CoSecJsonSerializer.readValue(it, Policy::class.java)
                DefaultPolicyEvaluator.evaluate(policy)
                policy
            }
        }
    }

    override fun authorize(request: Request, context: SecurityContext): Mono<AuthorizeResult> {
        if (context.principal.isRoot()) {
            return AuthorizeResult.ALLOW.toMono()
        }
        val verifyResult = policy.verify(request, context)
        if (verifyResult == VerifyResult.ALLOW) {
            return AuthorizeResult.ALLOW.toMono()
        }
        if (verifyResult == VerifyResult.EXPLICIT_DENY) {
            return AuthorizeResult.EXPLICIT_DENY.toMono()
        }

        val namespace = request.getNamespace() ?: return AuthorizeResult.IMPLICIT_DENY.toMono()

        val requestAction = ResourceAction(namespace, request.method.httpMethodAsAction())
        return checkRolePermissions(context.principal.roles, requestAction)
            .map { result: Boolean ->
                if (result) {
                    return@map AuthorizeResult.ALLOW
                }
                AuthorizeResult.IMPLICIT_DENY
            }
    }

    private fun checkRolePermissions(roleBind: Set<String>, requestAction: ResourceAction): Mono<Boolean> {
        return Flux.fromIterable(roleBind)
            .flatMap { roleName ->
                rbacService.getRole(roleName)
            }
            .any {
                it.check(
                    requestAction,
                )
            }
    }
}