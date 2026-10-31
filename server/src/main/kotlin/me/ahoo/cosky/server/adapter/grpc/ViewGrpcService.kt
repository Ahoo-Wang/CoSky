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

import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.v1.ServicesRequest
import me.ahoo.cosky.api.v1.ServicesResponse
import me.ahoo.cosky.api.v1.StatsRequest
import me.ahoo.cosky.api.v1.StatsResponse
import me.ahoo.cosky.api.v1.TopologyRequest
import me.ahoo.cosky.api.v1.TopologyResponse
import me.ahoo.cosky.api.v1.ViewServiceGrpcKt
import me.ahoo.cosky.server.view.ViewService
import org.springframework.stereotype.Service

/**
 * Protocol mapping only.
 */
@Service
class ViewGrpcService(
    private val views: ViewService,
) : ViewServiceGrpcKt.ViewServiceCoroutineImplBase(VirtualThreads.dispatcher) {
    override suspend fun services(request: ServicesRequest): ServicesResponse =
        grpcCall { views.services(request, AuthContext.caller()) }

    override suspend fun topology(request: TopologyRequest): TopologyResponse =
        grpcCall { views.topology(request, AuthContext.caller()) }

    override suspend fun stats(request: StatsRequest): StatsResponse =
        grpcCall { views.stats(request, AuthContext.caller()) }
}
