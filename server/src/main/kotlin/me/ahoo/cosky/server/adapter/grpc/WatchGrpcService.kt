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

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.WatchRequest
import me.ahoo.cosky.api.v1.WatchResponse
import me.ahoo.cosky.api.v1.WatchServiceGrpcKt
import me.ahoo.cosky.api.validation.Limits
import me.ahoo.cosky.api.validation.Validator
import me.ahoo.cosky.server.CoSkyProperties
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.server.watch.WatchSession
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.toKotlinDuration

/**
 * One stream multiplexes many watches (design §4.3). A watch that fails ends alone, with `canceled`, unless the whole
 * replica cannot serve it: then the stream ends, and the client moves all its watches to another endpoint (D29).
 */
@Service
class WatchGrpcService(
    private val caches: WatchCache,
    private val properties: CoSkyProperties,
) : WatchServiceGrpcKt.WatchServiceCoroutineImplBase(VirtualThreads.dispatcher) {
    override fun watch(requests: Flow<WatchRequest>): Flow<WatchResponse> = channelFlow {
        val watches = ConcurrentHashMap<Long, ActiveWatch>()
        val bookmarks = launch { sendProgress(watches) }
        requests.collect { request ->
            when (request.requestCase) {
                WatchRequest.RequestCase.CREATE -> create(request, watches)
                WatchRequest.RequestCase.CANCEL -> watches.remove(request.cancel.watchId)?.job?.cancel()
                else -> Unit
            }
        }
        bookmarks.join()
    }

    private suspend fun ProducerScope<WatchResponse>.create(
        request: WatchRequest,
        watches: MutableMap<Long, ActiveWatch>,
    ) {
        val create = request.create
        val violations = Validator.DEFAULT.validate(request)
        when {
            violations.isNotEmpty() ->
                send(WatchSession.canceled(create.watchId, CoSkyException.invalidArgument(violations)))

            watches.size >= properties.watch.maxPerStream -> send(
                WatchSession.canceled(create.watchId, CoSkyException.watchQuotaExceeded(properties.watch.maxPerStream)),
            )

            else -> {
                val session = WatchSession(
                    create,
                    caches.of(create.namespace),
                    Limits.DEFAULT.maxMessageBytes,
                    properties.read.minRevisionTimeout.toKotlinDuration(),
                ) { send(it) }
                val job = launch {
                    grpcCall { session.run() }
                    watches.remove(create.watchId)
                }
                watches[create.watchId] = ActiveWatch(session, job)
            }
        }
    }

    /**
     * One timer per stream, however many watches it holds.
     */
    private suspend fun ProducerScope<WatchResponse>.sendProgress(watches: Map<Long, ActiveWatch>) {
        while (true) {
            delay(properties.watch.progressInterval.toKotlinDuration())
            watches.forEach { (id, watch) -> send(WatchSession.progress(id, watch.session.cursor.get())) }
        }
    }

    private class ActiveWatch(val session: WatchSession, val job: Job)
}
