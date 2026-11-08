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

package me.ahoo.cosky.server.lease

import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.GrantRequest
import me.ahoo.cosky.api.v1.GrantResponse
import me.ahoo.cosky.api.v1.KeepAliveRequest
import me.ahoo.cosky.api.v1.KeepAliveResponse
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.RevokeRequest
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.api.validation.Limits
import me.ahoo.cosky.api.validation.Validator
import me.ahoo.cosky.api.validation.requireValid
import me.ahoo.cosky.server.auth.Authorizer
import me.ahoo.cosky.server.auth.Permission
import me.ahoo.cosky.server.auth.Principal
import me.ahoo.cosky.store.GrantResult
import me.ahoo.cosky.store.Store
import kotlin.time.Duration.Companion.seconds

/**
 * A lease is not a resource: granting one takes the right to write Instances, and only the grantor may use it
 * (design §6.2, §7.8).
 */
class LeaseService(
    private val store: Store,
    private val authorizer: Authorizer = Authorizer.ALLOW_ALL,
    private val limits: Limits = Limits.DEFAULT,
) {
    private val validator = Validator(limits)

    suspend fun grant(request: GrantRequest, principal: Principal): GrantResponse {
        requireValid(validator.validate(request))
        val permission = authorizer.permission(principal, Verb.VERB_PUT, Kind.KIND_INSTANCE, request.namespace)
        if (permission == Permission.None) {
            throw CoSkyException.permissionDenied(
                "${principal.id} may not write Instances in namespace ${request.namespace}.",
            )
        }
        val seconds = request.ttlSeconds.takeIf { it != 0 } ?: limits.defaultLeaseTtlSeconds
        return when (val result = store.grantLease(request.namespace, seconds.seconds, principal.id)) {
            is GrantResult.Granted -> GrantResponse.newBuilder().setLease(
                result.lease.id
            ).setTtlSeconds(seconds).build()
            GrantResult.NamespaceNotActive -> throw CoSkyException.namespaceNotFound(request.namespace)
        }
    }

    /**
     * The renewals of one stream, by one subject.
     */
    fun renewals(principal: Principal): Renewals = Renewals(principal)

    suspend fun revoke(request: RevokeRequest, principal: Principal) {
        requireValid(validator.validate(request))
        requireOwner(request.namespace, request.lease, principal)
        store.expireLease(request.namespace, request.lease, principal.id, force = true)
    }

    /**
     * A lease that no longer exists passes: that is what the response says, and it reveals nothing.
     *
     * @return whether the lease is known to be this subject's, which an id nobody holds yet is not.
     * @throws CoSkyException `PERMISSION_DENIED` when another subject holds it.
     */
    suspend fun requireOwner(namespace: String, lease: Long, principal: Principal): Boolean {
        if (principal == Principal.ANONYMOUS) {
            return true
        }
        val owner = store.leaseOwners(namespace, listOf(lease))[lease]
        if (owner != null && owner != principal.id) {
            throw CoSkyException.permissionDenied("Lease $lease belongs to another subject.")
        }
        return owner != null
    }

    inner class Renewals internal constructor(private val principal: Principal) {
        /**
         * Only the first renewal of a lease goes to the store for its owner (design §7.8). An id means something within
         * its namespace only.
         */
        private val owned = HashSet<Pair<String, Long>>()

        suspend fun keepAlive(request: KeepAliveRequest): KeepAliveResponse {
            requireValid(validator.validate(request))
            if (!authorizer.isValid(principal)) {
                throw CoSkyException.unauthenticated("The token is no longer valid.")
            }
            val lease = request.namespace to request.lease
            if (lease !in owned && requireOwner(request.namespace, request.lease, principal)) {
                owned += lease
            }
            val renewal = store.keepAlive(request.namespace, listOf(request.lease))[request.lease]
            return KeepAliveResponse.newBuilder()
                .setLease(request.lease)
                .setTtlSeconds(renewal?.ttl?.inWholeSeconds?.toInt() ?: 0)
                .setAttachedCount(renewal?.attached ?: 0)
                .build()
        }
    }
}
