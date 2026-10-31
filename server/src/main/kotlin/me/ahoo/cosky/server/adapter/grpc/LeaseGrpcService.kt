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

package me.ahoo.cosky.server.adapter.grpc

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.GrantRequest
import me.ahoo.cosky.api.v1.GrantResponse
import me.ahoo.cosky.api.v1.KeepAliveRequest
import me.ahoo.cosky.api.v1.KeepAliveResponse
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.LeaseServiceGrpcKt
import me.ahoo.cosky.api.v1.RevokeRequest
import me.ahoo.cosky.api.v1.RevokeResponse
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.api.validation.Limits
import me.ahoo.cosky.api.validation.Validator
import me.ahoo.cosky.api.validation.requireValid
import me.ahoo.cosky.server.auth.Authorizer
import me.ahoo.cosky.server.auth.Permission
import me.ahoo.cosky.server.auth.Principal
import me.ahoo.cosky.store.GrantResult
import me.ahoo.cosky.store.Store
import org.springframework.stereotype.Service
import kotlin.time.Duration.Companion.seconds

/**
 * A lease is not a resource: granting one takes the right to write Instances, and only the grantor may use it
 * (design §7.8).
 */
@Service
class LeaseGrpcService(
    private val store: Store,
    private val authorizer: Authorizer = Authorizer.ALLOW_ALL,
) : LeaseServiceGrpcKt.LeaseServiceCoroutineImplBase(VirtualThreads.dispatcher) {
    override suspend fun grant(request: GrantRequest): GrantResponse = grpcCall {
        requireValid(Validator.DEFAULT.validate(request))
        val principal = AuthContext.caller().principal
        val permission = authorizer.permission(principal, Verb.VERB_PUT, Kind.KIND_INSTANCE, request.namespace)
        if (permission == Permission.None) {
            throw CoSkyException.permissionDenied(
                "${principal.id} may not write Instances in namespace ${request.namespace}.",
            )
        }
        val seconds = request.ttlSeconds.takeIf { it != 0 } ?: Limits.DEFAULT.defaultLeaseTtlSeconds
        when (val result = store.grantLease(request.namespace, seconds.seconds, principal.id)) {
            is GrantResult.Granted ->
                GrantResponse.newBuilder().setLease(result.lease.id).setTtlSeconds(seconds).build()

            GrantResult.NamespaceNotActive -> throw CoSkyException.namespaceNotFound(request.namespace)
        }
    }

    override fun keepAlive(requests: Flow<KeepAliveRequest>): Flow<KeepAliveResponse> = flow {
        val principal = AuthContext.caller().principal
        // Only the first renewal of a lease on a stream goes to the store for its owner (design §7.8).
        val owned = HashSet<Long>()
        requests.collect { request ->
            emit(
                grpcCall {
                    requireValid(Validator.DEFAULT.validate(request))
                    if (!authorizer.isValid(principal)) {
                        throw CoSkyException.unauthenticated("The token is no longer valid.")
                    }
                    if (request.lease !in owned) {
                        requireOwner(request.namespace, request.lease, principal)
                        owned += request.lease
                    }
                    val renewal = store.keepAlive(request.namespace, listOf(request.lease))[request.lease]
                    KeepAliveResponse.newBuilder()
                        .setLease(request.lease)
                        .setTtlSeconds(renewal?.ttl?.inWholeSeconds?.toInt() ?: 0)
                        .setAttachedCount(renewal?.attached ?: 0)
                        .build()
                },
            )
        }
    }

    override suspend fun revoke(request: RevokeRequest): RevokeResponse = grpcCall {
        requireValid(Validator.DEFAULT.validate(request))
        val principal = AuthContext.caller().principal
        requireOwner(request.namespace, request.lease, principal)
        store.expireLease(request.namespace, request.lease, principal.id, force = true)
        RevokeResponse.getDefaultInstance()
    }

    /**
     * A lease that no longer exists passes: that is what the response says, and it reveals nothing.
     */
    private suspend fun requireOwner(namespace: String, lease: Long, principal: Principal) {
        if (principal == Principal.ANONYMOUS) {
            return
        }
        val owner = store.leaseOwners(namespace, listOf(lease))[lease]
        if (owner != null && owner != principal.id) {
            throw CoSkyException.permissionDenied("Lease $lease belongs to another subject.")
        }
    }
}
