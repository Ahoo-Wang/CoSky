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
import me.ahoo.cosky.api.v1.DeleteRequest
import me.ahoo.cosky.api.v1.DeleteResponse
import me.ahoo.cosky.api.v1.GetRequest
import me.ahoo.cosky.api.v1.HistoryRequest
import me.ahoo.cosky.api.v1.HistoryResponse
import me.ahoo.cosky.api.v1.ListRequest
import me.ahoo.cosky.api.v1.ListResponse
import me.ahoo.cosky.api.v1.PutRequest
import me.ahoo.cosky.api.v1.PutResponse
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceServiceGrpcKt
import me.ahoo.cosky.server.resource.ResourceService
import org.springframework.stereotype.Service

/**
 * Protocol mapping only.
 */
@Service
class ResourceGrpcService(
    private val resources: ResourceService,
) : ResourceServiceGrpcKt.ResourceServiceCoroutineImplBase(VirtualThreads.dispatcher) {
    override suspend fun get(request: GetRequest): Resource = grpcCall { resources.get(request) }

    override suspend fun list(request: ListRequest): ListResponse = grpcCall { resources.list(request) }

    override suspend fun put(request: PutRequest): PutResponse = grpcCall { resources.put(request, ACTOR) }

    override suspend fun delete(request: DeleteRequest): DeleteResponse = grpcCall { resources.delete(request, ACTOR) }

    override suspend fun history(request: HistoryRequest): HistoryResponse = grpcCall { resources.history(request) }

    private companion object {
        /** Authentication comes with W3a; until then every write has the same author. */
        const val ACTOR = "anonymous"
    }
}
