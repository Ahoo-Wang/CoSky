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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Rule
import me.ahoo.cosky.api.v1.Subject
import me.ahoo.cosky.api.v1.TokenRef
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.watch.NamespaceCache
import me.ahoo.cosky.server.watch.NamespaceView
import me.ahoo.cosky.server.watch.WatchCache
import java.time.Clock
import java.util.concurrent.atomic.AtomicReference

/**
 * Authentication and authorization from the Watch Cache of `_system`, which needs no trip to the store (design §6.3).
 */
class AccessControl(
    private val caches: WatchCache,
    private val clock: Clock = Clock.systemUTC(),
    private val oidc: OidcAuthenticator? = null,
) : Authorizer {
    private class Snapshot(val epoch: String, val revision: Long, val model: AccessModel)

    private val current = AtomicReference<Snapshot?>()

    private val system: NamespaceCache
        get() = caches.of(NamingRules.SYSTEM_NAMESPACE)

    override val changes: Flow<Unit>
        get() = system.views.filterNotNull().distinctUntilChangedBy { it.epoch to it.revision }.map { }

    /**
     * @param plaintext a ServiceAccount token or, where OIDC is configured, an ID token.
     * @throws CoSkyException with `UNAUTHENTICATED` unless the token is valid; `UNAVAILABLE` while rules are loading.
     */
    fun authenticate(plaintext: String?): Principal {
        val token = plaintext?.let { Tokens.parse(it) }
        if (token == null && plaintext != null && oidc != null) {
            return oidc.authenticate(plaintext)
        }
        val account = token?.let { model().token(it.id) }?.takeIf { (_, ref) ->
            !isExpired(ref) && token.matches(ref.sha256)
        }
        if (token == null || account == null) {
            throw CoSkyException.unauthenticated("A valid token is required.")
        }
        return Principal(Subject.Type.TYPE_SERVICE_ACCOUNT, account.first, tokenId = token.id)
    }

    override fun isValid(principal: Principal): Boolean {
        val id = principal.tokenId ?: return true
        val ref = model().token(id)?.second ?: return false
        return !isExpired(ref)
    }

    override fun permission(principal: Principal, verb: Verb, kind: Kind, namespace: String): Permission =
        model().permission(principal, verb, kind, namespace)

    /**
     * What a principal may do, for `/v1/me`.
     */
    fun rulesOf(principal: Principal): List<Rule> = model().rulesOf(principal)

    private fun isExpired(ref: TokenRef): Boolean =
        ref.hasExpiresAt() && clock.instant().epochSecond >= ref.expiresAt.seconds

    private fun model(): AccessModel {
        val view = system.views.value ?: throw CoSkyException.storeUnavailable("Access rules are loading.")
        return modelOf(view)
    }

    private fun modelOf(view: NamespaceView): AccessModel {
        val cached = current.get()
        // A revision only means something within its epoch: a timeline that was replaced can hold the same number.
        if (cached != null && cached.epoch == view.epoch && cached.revision == view.revision) {
            return cached.model
        }
        val model = AccessModel(view.objects.values)
        current.set(Snapshot(view.epoch, view.revision, model))
        return model
    }
}
