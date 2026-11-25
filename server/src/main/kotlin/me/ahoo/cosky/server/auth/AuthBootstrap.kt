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
import me.ahoo.cosky.server.BOOTSTRAP_TOKEN_EXAMPLE
import me.ahoo.cosky.server.StartupFailure
import me.ahoo.cosky.store.ApplyResult
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.Store
import org.slf4j.LoggerFactory

/**
 * The first start: with nothing bound yet, the bootstrap token becomes an `admin` ServiceAccount (design §6.3). Later
 * starts find a RoleBinding and do not use the token. The bindings the server gives namespaces do not count (design
 * D45): `default` would have one before anyone could sign in.
 *
 * A token that is set is checked at every start all the same, used or not: a store that lost its data is set up from it
 * again while the server runs (design §7.2), when there is nobody to tell that it is weak.
 */
class AuthBootstrap(private val store: Store) {
    suspend fun run(bootstrapToken: String?) {
        val token = bootstrapToken?.takeIf { it.isNotEmpty() }?.let(::parse)
        val bound = store.readAll(NamingRules.SYSTEM_NAMESPACE)
            .firstOrNull { it.hasRoleBinding() && !NamespaceAccounts.isManaged(it) }
        if (bound != null) {
            return
        }
        if (token == null) {
            log.warn(
                "No RoleBinding exists and no bootstrap token is set, so nobody can sign in. Set " +
                    "cosky.auth.bootstrap-token (COSKY_BOOTSTRAP_TOKEN), such as {}, and start again.",
                BOOTSTRAP_TOKEN_EXAMPLE,
            )
            return
        }
        val result = store.apply(
            NamingRules.SYSTEM_NAMESPACE,
            listOf(
                Mutation.Put(account(token), Mutation.MUST_NOT_EXIST),
                Mutation.Put(binding(), Mutation.MUST_NOT_EXIST)
            ),
            ACTOR,
        )
        when (result) {
            is ApplyResult.Applied -> log.info("Created ServiceAccount {} from the bootstrap token.", ADMIN)
            // Replicas start together, and only one of them gets to write this.
            is ApplyResult.Conflict -> log.info("ServiceAccount {} exists already; not using the token.", ADMIN)
            else -> error("Bootstrapping failed: $result.")
        }
    }

    /**
     * Neither message shows the token, which goes to the log and the console.
     */
    private fun parse(plaintext: String): ParsedToken {
        val token = Tokens.parse(plaintext) ?: throw StartupFailure(
            "The bootstrap token must look like ${ParsedToken.PREFIX}<id>${ParsedToken.SEPARATOR}<secret>, " +
                "both base62.",
            "Set cosky.auth.bootstrap-token (COSKY_BOOTSTRAP_TOKEN) to one, such as $BOOTSTRAP_TOKEN_EXAMPLE.",
        )
        if (token.secret.length < MIN_SECRET_LENGTH) {
            throw StartupFailure(
                "The secret of the bootstrap token, after its second ${ParsedToken.SEPARATOR}, has " +
                    "${token.secret.length} characters: it takes at least $MIN_SECRET_LENGTH base62 characters, " +
                    "about 128 bits, so that nobody can guess the admin's token.",
                "Set cosky.auth.bootstrap-token (COSKY_BOOTSTRAP_TOKEN) to a new one, such as " +
                    "$BOOTSTRAP_TOKEN_EXAMPLE.",
            )
        }
        return token
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

    companion object {
        /**
         * Each base62 character carries almost 6 bits, so 22 make about 128. What [Tokens.generate] makes has 43.
         */
        const val MIN_SECRET_LENGTH = 22

        private const val ADMIN = AccessModel.ADMIN_ROLE
        private const val BINDING = "admin-bootstrap"
        private const val ACTOR = "system"
        private val log = LoggerFactory.getLogger(AuthBootstrap::class.java)
    }
}
