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
import me.ahoo.cosky.api.v1.RevokeTokenResponse
import me.ahoo.cosky.api.v1.TokenRef
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.api.v1.ViolationCode
import me.ahoo.cosky.api.validation.Limits
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.api.validation.Violation
import me.ahoo.cosky.server.audit.AuditLogger
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
 * compare-and-set on the account, so two at once cannot lose one another; a caller may name the revision the account
 * must still be at, as for any write.
 */
class TokenService(
    private val store: Store,
    private val caches: WatchCache,
    private val authorizer: Authorizer = Authorizer.ALLOW_ALL,
    private val limits: Limits = Limits.DEFAULT,
    private val timeout: Duration = 3.seconds,
    private val clock: Clock = Clock.systemUTC(),
    private val audit: AuditLogger = AuditLogger(),
) {
    /**
     * @param token its plaintext, which is never available again.
     * @param revision of the account, which holding the token created.
     */
    data class CreatedToken(val token: ParsedToken, val expiresAt: Instant?, val revision: Long)

    /**
     * @param expectedRevision of the account; [Mutation.NO_CHECK] for none.
     */
    suspend fun create(
        account: String,
        expiresAt: Instant?,
        caller: Caller,
        expectedRevision: Long = Mutation.NO_CHECK,
    ): CreatedToken {
        val token = Tokens.generate()
        val ref = TokenRef.newBuilder().setId(token.id).setSha256(Tokens.sha256Hex(token.secret))
        expiresAt?.let { ref.setExpiresAt(Timestamps.fromMillis(it.toEpochMilli())) }
        val written = audit.audited(caller, Verb.VERB_PUT, tokenKey(account, token.id), Written::revision) {
            requirePermission(account, caller)
            // A token is the account: only for someone who holds whatever the account may do (design §7.8).
            authorizer.requireHeld(caller.principal, Grant.OfAccount(account))
            update(account, caller, expectedRevision) { current ->
                // An expired token is dead weight: it should not stand in the way of rotation.
                val valid = current.filterNot {
                    it.hasExpiresAt() && clock.instant().epochSecond >= it.expiresAt.seconds
                }
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
        }
        return CreatedToken(token, expiresAt, written.revision)
    }

    /**
     * A token the account does not hold is not an error: there is nothing to revoke, and the answer says so.
     *
     * @param expectedRevision of the account; [Mutation.NO_CHECK] for none.
     */
    suspend fun revoke(
        account: String,
        tokenId: String,
        caller: Caller,
        expectedRevision: Long = Mutation.NO_CHECK,
    ): RevokeTokenResponse {
        val written = audit.audited(caller, Verb.VERB_DELETE, tokenKey(account, tokenId), Written::revision) {
            requirePermission(account, caller)
            update(account, caller, expectedRevision) { current -> current.filterNot { it.id == tokenId } }
        }
        return RevokeTokenResponse.newBuilder().setRevision(written.revision).setDeleted(written.changed).build()
    }

    /**
     * @param revision of the account after the change: the one it wrote, or the one the account has when there was
     * nothing to change.
     */
    private data class Written(val revision: Long, val changed: Boolean)

    /**
     * What the audit log names: the id of a token says which one it was, and only the secret is secret.
     */
    private fun tokenKey(account: String, tokenId: String): ResourceKey =
        accountKey(account).toBuilder().setName("$account/tokens/$tokenId").build()

    private fun accountKey(account: String): ResourceKey = ResourceKey.newBuilder()
        .setNamespace(NamingRules.SYSTEM_NAMESPACE)
        .setKind(Kind.KIND_SERVICE_ACCOUNT)
        .setName(account)
        .build()

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

    /**
     * Without an expected revision, a write in between has the change made again on the account as it is then; with
     * one, it is a conflict, as the account is no longer at that revision. A change that leaves the tokens as they are
     * writes nothing.
     */
    private suspend fun update(
        account: String,
        caller: Caller,
        expectedRevision: Long,
        change: (List<TokenRef>) -> List<TokenRef>,
    ): Written {
        val key = accountKey(account)
        val expected = expectedRevision.takeIf { it > 0 }
        var minRevision: Long? = null
        repeat(ATTEMPTS) {
            val current = currentOf(key, minRevision)
            requireAt(key, expected, current.meta.revision)
            val tokens = current.serviceAccount.tokensList
            val changed = change(tokens)
            if (changed == tokens) {
                return Written(current.meta.revision, changed = false)
            }
            val mutation = Mutation.Put(withTokens(current, changed), current.meta.revision)
            when (val result = store.apply(NamingRules.SYSTEM_NAMESPACE, listOf(mutation), caller.principal.id)) {
                is ApplyResult.Applied -> return Written(result.revision, changed = true)
                is ApplyResult.Conflict -> {
                    // With an expected revision, which the write compared with, this throws.
                    requireAt(key, expected, result.currentRevision ?: 0)
                    minRevision = result.currentRevision
                }

                else -> throw failureOf(account, result)
            }
        }
        throw CoSkyException.revisionConflict(key, 0, minRevision ?: 0)
    }

    /**
     * @throws CoSkyException with `REVISION_CONFLICT` unless the account is at [expected], when there is one.
     */
    private fun requireAt(key: ResourceKey, expected: Long?, current: Long) {
        if (expected != null && current != expected) {
            throw CoSkyException.revisionConflict(key, expected, current)
        }
    }

    private fun failureOf(account: String, result: ApplyResult): Exception = when (result) {
        ApplyResult.NamespaceNotActive -> CoSkyException.namespaceNotFound(NamingRules.SYSTEM_NAMESPACE)
        else -> IllegalStateException("Updating the tokens of $account failed: $result.")
    }

    /**
     * The account as of [minRevision], or, with none to go by, as of the store's latest write: an account written a
     * moment ago, perhaps through another replica, is there to take a token, and an expected revision is compared with
     * the latest one, rather than waited for.
     */
    private suspend fun currentOf(key: ResourceKey, minRevision: Long?): Resource {
        val view = if (minRevision == null) {
            caches.latestViewAt(NamingRules.SYSTEM_NAMESPACE, timeout)
        } else {
            caches.viewAt(NamingRules.SYSTEM_NAMESPACE, minRevision, timeout)
        }
        return view.objects[key] ?: throw CoSkyException.resourceNotFound(key)
    }

    private fun withTokens(account: Resource, tokens: List<TokenRef>): Resource = account.toBuilder()
        .apply { serviceAccountBuilder.clearTokens().addAllTokens(tokens) }
        .build()

    private companion object {
        const val ATTEMPTS = 3
    }
}
