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
import me.ahoo.cosky.api.v1.GrantRequest
import me.ahoo.cosky.api.v1.GrantResponse
import me.ahoo.cosky.api.v1.KeepAliveRequest
import me.ahoo.cosky.api.v1.KeepAliveResponse
import me.ahoo.cosky.api.v1.LeaseServiceGrpcKt
import me.ahoo.cosky.api.v1.RevokeRequest
import me.ahoo.cosky.api.v1.RevokeResponse
import me.ahoo.cosky.server.lease.LeaseService
import org.springframework.stereotype.Service

/**
 * Leases over gRPC; what they mean is [LeaseService]'s.
 */
@Service
class LeaseGrpcService(private val leases: LeaseService) :
    LeaseServiceGrpcKt.LeaseServiceCoroutineImplBase(VirtualThreads.dispatcher) {
    override suspend fun grant(request: GrantRequest): GrantResponse = grpcCall {
        leases.grant(request, AuthContext.caller().principal)
    }

    override fun keepAlive(requests: Flow<KeepAliveRequest>): Flow<KeepAliveResponse> = flow {
        val renewals = leases.renewals(AuthContext.caller().principal)
        requests.collect { request -> emit(grpcCall { renewals.keepAlive(request) }) }
    }

    override suspend fun revoke(request: RevokeRequest): RevokeResponse = grpcCall {
        leases.revoke(request, AuthContext.caller().principal)
        RevokeResponse.getDefaultInstance()
    }
}
