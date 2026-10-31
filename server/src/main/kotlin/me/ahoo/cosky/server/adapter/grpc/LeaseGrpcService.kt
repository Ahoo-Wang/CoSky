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
import kotlinx.coroutines.flow.map
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.GrantRequest
import me.ahoo.cosky.api.v1.GrantResponse
import me.ahoo.cosky.api.v1.KeepAliveRequest
import me.ahoo.cosky.api.v1.KeepAliveResponse
import me.ahoo.cosky.api.v1.LeaseServiceGrpcKt
import me.ahoo.cosky.api.v1.RevokeRequest
import me.ahoo.cosky.api.v1.RevokeResponse
import me.ahoo.cosky.api.validation.Limits
import me.ahoo.cosky.api.validation.Validator
import me.ahoo.cosky.api.validation.requireValid
import me.ahoo.cosky.store.GrantResult
import me.ahoo.cosky.store.Store
import org.springframework.stereotype.Service
import kotlin.time.Duration.Companion.seconds

/**
 * The lease path of the integration check (roadmap W2c). Ownership is enforced once authentication exists (W3a).
 */
@Service
class LeaseGrpcService(
    private val store: Store,
) : LeaseServiceGrpcKt.LeaseServiceCoroutineImplBase(VirtualThreads.dispatcher) {
    override suspend fun grant(request: GrantRequest): GrantResponse = grpcCall {
        requireValid(Validator.DEFAULT.validate(request))
        val seconds = request.ttlSeconds.takeIf { it != 0 } ?: Limits.DEFAULT.defaultLeaseTtlSeconds
        when (val result = store.grantLease(request.namespace, seconds.seconds, ACTOR)) {
            is GrantResult.Granted ->
                GrantResponse.newBuilder().setLease(result.lease.id).setTtlSeconds(seconds).build()

            GrantResult.NamespaceNotActive -> throw CoSkyException.namespaceNotFound(request.namespace)
        }
    }

    override fun keepAlive(requests: Flow<KeepAliveRequest>): Flow<KeepAliveResponse> = requests.map { request ->
        grpcCall {
            requireValid(Validator.DEFAULT.validate(request))
            val renewal = store.keepAlive(request.namespace, listOf(request.lease))[request.lease]
            KeepAliveResponse.newBuilder()
                .setLease(request.lease)
                .setTtlSeconds(renewal?.ttl?.inWholeSeconds?.toInt() ?: 0)
                .setAttachedCount(renewal?.attached ?: 0)
                .build()
        }
    }

    override suspend fun revoke(request: RevokeRequest): RevokeResponse = grpcCall {
        requireValid(Validator.DEFAULT.validate(request))
        store.expireLease(request.namespace, request.lease, ACTOR, force = true)
        RevokeResponse.getDefaultInstance()
    }

    private companion object {
        const val ACTOR = "anonymous"
    }
}
