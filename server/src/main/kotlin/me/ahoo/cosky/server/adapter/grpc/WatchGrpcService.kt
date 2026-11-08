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
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.v1.WatchRequest
import me.ahoo.cosky.api.v1.WatchResponse
import me.ahoo.cosky.api.v1.WatchServiceGrpcKt
import me.ahoo.cosky.server.auth.Principal
import me.ahoo.cosky.server.watch.WatchService
import org.springframework.stereotype.Service

/**
 * One stream multiplexes many watches (design §4.3). A watch that fails ends alone, with `canceled`, unless the whole
 * replica cannot serve it: then the stream ends, and the client moves all its watches to another endpoint (D29).
 */
@Service
class WatchGrpcService(private val watches: WatchService) :
    WatchServiceGrpcKt.WatchServiceCoroutineImplBase(VirtualThreads.dispatcher) {
    override fun watch(requests: Flow<WatchRequest>): Flow<WatchResponse> = channelFlow {
        val principal = AuthContext.caller().principal
        val consumer = ClientNameInterceptor.KEY.get() ?: principal.name.takeIf { principal != Principal.ANONYMOUS }
        val stream = watches.stream(principal, consumer, this) { send(it) }
        val maintaining = launch { stream.maintain() }
        requests.collect { request ->
            when (request.requestCase) {
                WatchRequest.RequestCase.CREATE -> stream.create(request.create)
                WatchRequest.RequestCase.CANCEL -> stream.cancel(request.cancel.watchId)
                else -> Unit
            }
        }
        maintaining.join()
    }.grpcErrors()
}
