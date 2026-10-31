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

import com.google.protobuf.util.Timestamps
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.TokenRef
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.api.v1.ViolationCode
import me.ahoo.cosky.api.validation.Limits
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.api.validation.Violation
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.ApplyResult
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.Store
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Creates and revokes ServiceAccount tokens (design §7.8), the only way to change them. Each change is a
 * compare-and-set on the account, so two at once cannot lose one another.
 */
class TokenService(
    private val store: Store,
    private val caches: WatchCache,
    private val authorizer: Authorizer = Authorizer.ALLOW_ALL,
    private val limits: Limits = Limits.DEFAULT,
    private val timeout: Duration = 3.seconds,
    private val clock: Clock = Clock.systemUTC(),
) {
    /**
     * @param token its plaintext, which is never available again.
     */
    data class CreatedToken(val token: ParsedToken, val expiresAt: Instant?)

    suspend fun create(account: String, expiresAt: Instant?, caller: Caller): CreatedToken {
        requirePermission(account, caller)
        val token = Tokens.generate()
        val ref = TokenRef.newBuilder().setId(token.id).setSha256(Tokens.sha256Hex(token.secret))
        expiresAt?.let { ref.setExpiresAt(Timestamps.fromMillis(it.toEpochMilli())) }
        update(account, caller) { current ->
            // An expired token is dead weight: it should not stand in the way of rotation.
            val valid = current.filterNot { it.hasExpiresAt() && clock.instant().epochSecond >= it.expiresAt.seconds }
            if (valid.size >= limits.maxTokens) {
                throw CoSkyException.invalidArgument(
                    listOf(
                        Violation(
                            "tokens",
                            ViolationCode.VIOLATION_CODE_TOO_MANY,
                            "An account holds at most ${limits.maxTokens} valid tokens; revoke one first.",
                        ),
                    ),
                )
            }
            valid + ref.build()
        }
        return CreatedToken(token, expiresAt)
    }

    suspend fun revoke(account: String, tokenId: String, caller: Caller) {
        requirePermission(account, caller)
        update(account, caller) { current -> current.filterNot { it.id == tokenId } }
    }

    private fun requirePermission(account: String, caller: Caller) {
        val permission = authorizer.permission(
            caller.principal,
            Verb.VERB_PUT,
            Kind.KIND_SERVICE_ACCOUNT,
            NamingRules.SYSTEM_NAMESPACE,
        )
        if (!permission.allows(account)) {
            throw CoSkyException.permissionDenied("${caller.principal.id} may not manage the tokens of $account.")
        }
    }

    private suspend fun update(account: String, caller: Caller, change: (List<TokenRef>) -> List<TokenRef>) {
        val key = ResourceKey.newBuilder()
            .setNamespace(NamingRules.SYSTEM_NAMESPACE)
            .setKind(Kind.KIND_SERVICE_ACCOUNT)
            .setName(account)
            .build()
        var minRevision = 0L
        repeat(ATTEMPTS) {
            val current = caches.viewAt(NamingRules.SYSTEM_NAMESPACE, minRevision, timeout).objects[key]
                ?: throw CoSkyException.resourceNotFound(key)
            val updated = withTokens(current, change(current.serviceAccount.tokensList))
            val mutation = Mutation.Put(updated, current.meta.revision)
            when (val result = store.apply(NamingRules.SYSTEM_NAMESPACE, listOf(mutation), caller.principal.id)) {
                is ApplyResult.Applied -> return
                is ApplyResult.Conflict -> minRevision = result.currentRevision ?: 0
                else -> error("Updating the tokens of $account failed: $result.")
            }
        }
        throw CoSkyException.revisionConflict(key, 0, minRevision)
    }

    private fun withTokens(account: Resource, tokens: List<TokenRef>): Resource = account.toBuilder()
        .apply { serviceAccountBuilder.clearTokens().addAllTokens(tokens) }
        .build()

    private companion object {
        const val ATTEMPTS = 3
    }
}
