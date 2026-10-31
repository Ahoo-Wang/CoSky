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
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.api.v1.WatchRequest
import me.ahoo.cosky.api.v1.WatchResponse
import me.ahoo.cosky.api.v1.WatchServiceGrpcKt
import me.ahoo.cosky.api.validation.Limits
import me.ahoo.cosky.api.validation.Validator
import me.ahoo.cosky.server.CoSkyProperties
import me.ahoo.cosky.server.auth.Authorizer
import me.ahoo.cosky.server.auth.Permission
import me.ahoo.cosky.server.auth.Principal
import me.ahoo.cosky.server.view.TopologyRecorder
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
    private val authorizer: Authorizer = Authorizer.ALLOW_ALL,
    private val topology: TopologyRecorder? = null,
) : WatchServiceGrpcKt.WatchServiceCoroutineImplBase(VirtualThreads.dispatcher) {
    override fun watch(requests: Flow<WatchRequest>): Flow<WatchResponse> = channelFlow {
        val principal = AuthContext.caller().principal
        val consumer = ClientNameInterceptor.KEY.get() ?: principal.name.takeIf { principal != Principal.ANONYMOUS }
        val watches = ConcurrentHashMap<Long, ActiveWatch>()
        val bookmarks = launch { sendProgress(watches) }
        launch { authorizer.changes.collect { grpcCall { reevaluate(principal, watches) } } }
        requests.collect { request ->
            when (request.requestCase) {
                WatchRequest.RequestCase.CREATE -> grpcCall { create(request, principal, consumer, watches) }
                WatchRequest.RequestCase.CANCEL -> watches.remove(request.cancel.watchId)?.job?.cancel()
                else -> Unit
            }
        }
        bookmarks.join()
    }

    /**
     * What a watch delivered was filtered by what its subject could see, so when that changes it has to start over
     * (design §7.3). A revoked token ends the whole stream.
     */
    private suspend fun ProducerScope<WatchResponse>.reevaluate(
        principal: Principal,
        watches: MutableMap<Long, ActiveWatch>,
    ) {
        if (!authorizer.isValid(principal)) {
            throw CoSkyException.unauthenticated("The token is no longer valid.")
        }
        watches.forEach { (id, watch) ->
            val now = authorizer.permission(principal, Verb.VERB_WATCH, watch.create.kind, watch.create.namespace)
            if (now != watch.permission) {
                watches.remove(id)
                watch.job.cancel()
                send(
                    if (now == Permission.None) {
                        WatchSession.canceled(id, denied(principal, watch.create))
                    } else {
                        WatchSession.gone(id, 0)
                    },
                )
            }
        }
    }

    private fun denied(principal: Principal, create: WatchRequest.Create): CoSkyException =
        CoSkyException.permissionDenied(
            "${principal.id} may not watch ${create.kind.name.removePrefix("KIND_")} in namespace ${create.namespace}.",
        )

    private suspend fun ProducerScope<WatchResponse>.create(
        request: WatchRequest,
        principal: Principal,
        consumer: String?,
        watches: MutableMap<Long, ActiveWatch>,
    ) {
        val create = request.create
        val violations = Validator.DEFAULT.validate(request)
        val permission = if (violations.isEmpty()) {
            authorizer.permission(principal, Verb.VERB_WATCH, create.kind, create.namespace)
        } else {
            Permission.None
        }
        when {
            violations.isNotEmpty() ->
                send(WatchSession.canceled(create.watchId, CoSkyException.invalidArgument(violations)))

            permission == Permission.None -> send(WatchSession.canceled(create.watchId, denied(principal, create)))

            watches.size >= properties.watch.maxPerStream -> send(
                WatchSession.canceled(create.watchId, CoSkyException.watchQuotaExceeded(properties.watch.maxPerStream)),
            )

            else -> {
                val session = WatchSession(
                    create,
                    caches.of(create.namespace),
                    Limits.DEFAULT.maxMessageBytes,
                    properties.read.minRevisionTimeout.toKotlinDuration(),
                    permission,
                ) { send(it) }
                val edge = recordTopology(create, consumer)
                val job = launch {
                    grpcCall { session.run() }
                    watches.remove(create.watchId)
                }
                job.invokeOnCompletion { edge?.close() }
                watches[create.watchId] = ActiveWatch(create, permission, session, job)
            }
        }
    }

    /**
     * Only a watch of one service says what its client depends on: one of the whole kind does not (design §6.3).
     */
    private fun recordTopology(create: WatchRequest.Create, consumer: String?): AutoCloseable? {
        if (consumer == null || create.kind != Kind.KIND_INSTANCE || create.service.isEmpty()) {
            return null
        }
        return topology?.watching(create.namespace, consumer, create.service)
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

    private class ActiveWatch(
        val create: WatchRequest.Create,
        val permission: Permission,
        val session: WatchSession,
        val job: Job,
    )
}
