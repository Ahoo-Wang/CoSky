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

package me.ahoo.cosky.server.auth

import kotlinx.coroutines.flow.firstOrNull
import me.ahoo.cosky.api.v1.Subject
import me.ahoo.cosky.api.v1.TokenRef
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.v1.roleBindingSpec
import me.ahoo.cosky.api.v1.serviceAccountSpec
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.store.ApplyResult
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.Store
import org.slf4j.LoggerFactory

/**
 * The first start: with nothing bound yet, the bootstrap token becomes an `admin` ServiceAccount (design §6.3). Later
 * starts find a RoleBinding and ignore the token, so removing it from the environment changes nothing.
 */
class AuthBootstrap(private val store: Store) {
    suspend fun run(bootstrapToken: String?) {
        if (store.readAll(NamingRules.SYSTEM_NAMESPACE).firstOrNull { it.hasRoleBinding() } != null) {
            return
        }
        if (bootstrapToken.isNullOrEmpty()) {
            log.warn("No RoleBinding exists and no bootstrap token is set, so nobody can sign in.")
            return
        }
        val token = checkNotNull(Tokens.parse(bootstrapToken)) {
            "The bootstrap token must look like ${ParsedToken.PREFIX}<id>${ParsedToken.SEPARATOR}<secret>, both base62."
        }
        val result = store.apply(
            NamingRules.SYSTEM_NAMESPACE,
            listOf(
                Mutation.Put(account(token), Mutation.MUST_NOT_EXIST),
                Mutation.Put(binding(), Mutation.MUST_NOT_EXIST)
            ),
            ACTOR,
        )
        check(result is ApplyResult.Applied) { "Bootstrapping failed: $result." }
        log.info("Created ServiceAccount {} from the bootstrap token.", ADMIN)
    }

    private fun account(token: ParsedToken) = resource {
        meta = objectMeta {
            namespace = NamingRules.SYSTEM_NAMESPACE
            name = ADMIN
        }
        serviceAccount = serviceAccountSpec {
            tokens += TokenRef.newBuilder().setId(token.id).setSha256(Tokens.sha256Hex(token.secret)).build()
        }
    }

    private fun binding() = resource {
        meta = objectMeta {
            namespace = NamingRules.SYSTEM_NAMESPACE
            name = BINDING
        }
        roleBinding = roleBindingSpec {
            role = ADMIN
            subjects += Subject.newBuilder().setType(Subject.Type.TYPE_SERVICE_ACCOUNT).setName(ADMIN).build()
            namespaces += Permission.WILDCARD
        }
    }

    private companion object {
        const val ADMIN = AccessModel.ADMIN_ROLE
        const val BINDING = "admin-bootstrap"
        const val ACTOR = "system"
        val log = LoggerFactory.getLogger(AuthBootstrap::class.java)
    }
}
