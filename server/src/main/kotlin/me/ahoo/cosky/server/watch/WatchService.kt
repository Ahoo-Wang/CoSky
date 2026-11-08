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

package me.ahoo.cosky.server.watch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.api.v1.ViolationCode
import me.ahoo.cosky.api.v1.WatchRequest
import me.ahoo.cosky.api.v1.WatchResponse
import me.ahoo.cosky.api.validation.Limits
import me.ahoo.cosky.api.validation.Validator
import me.ahoo.cosky.api.validation.Violation
import me.ahoo.cosky.api.validation.requireValid
import me.ahoo.cosky.server.CoSkyProperties
import me.ahoo.cosky.server.auth.Authorizer
import me.ahoo.cosky.server.auth.Permission
import me.ahoo.cosky.server.auth.Principal
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.toKotlinDuration

/**
 * Watches as both protocols serve them (design §4.3, §7.3): a watch is checked, limited, bookmarked and re-evaluated
 * here, so that an adapter only maps requests in and responses out.
 *
 * @param topology records what clients watch; null records nothing.
 */
class WatchService(
    private val caches: WatchCache,
    private val properties: CoSkyProperties,
    private val authorizer: Authorizer = Authorizer.ALLOW_ALL,
    private val topology: TopologyRecorder? = null,
    private val limits: Limits = Limits.DEFAULT,
) {
    private val validator = Validator(limits)

    /**
     * What [principal] may see of what [create] asks for.
     *
     * @throws CoSkyException when it is invalid or not allowed.
     */
    fun permissionOf(principal: Principal, create: WatchRequest.Create): Permission {
        requireValid(validator.validate(WatchRequest.newBuilder().setCreate(create).build()))
        val permission = authorizer.permission(principal, Verb.VERB_WATCH, create.kind, create.namespace)
        if (permission == Permission.None) {
            throw denied(principal, create)
        }
        return permission
    }

    private fun denied(principal: Principal, create: WatchRequest.Create): CoSkyException =
        CoSkyException.permissionDenied(
            "${principal.id} may not watch ${create.kind.name.removePrefix("KIND_")} in namespace ${create.namespace}.",
        )

    /**
     * @param consumer what the client calls itself, for the topology.
     * @param scope where the watches run: the stream's, so that a replica unable to serve one ends the stream, and the
     * client moves all its watches to another endpoint (design D29).
     * @param send delivers a response; it suspends while the stream is full, which holds back that watch only.
     */
    fun stream(
        principal: Principal,
        consumer: String?,
        scope: CoroutineScope,
        send: suspend (WatchResponse) -> Unit,
    ): WatchStream = WatchStream(principal, consumer, scope, send)

    /**
     * The watches of one stream, all for one subject.
     */
    inner class WatchStream internal constructor(
        private val principal: Principal,
        private val consumer: String?,
        private val scope: CoroutineScope,
        private val send: suspend (WatchResponse) -> Unit,
    ) {
        private val watches = ConcurrentHashMap<Long, ActiveWatch>()

        /**
         * Starts a watch, or refuses it with `canceled`, which leaves the rest of the stream as it was.
         */
        suspend fun create(create: WatchRequest.Create) {
            val permission = try {
                admit(create)
            } catch (e: CoSkyException) {
                send(WatchSession.canceled(create.watchId, e))
                return
            }
            start(create, permission)
        }

        private fun admit(create: WatchRequest.Create): Permission {
            val permission = permissionOf(principal, create)
            // The one it would replace would go on, out of reach of Cancel, the quota and a change of permissions.
            if (watches.containsKey(create.watchId)) {
                throw CoSkyException.invalidArgument(
                    listOf(
                        Violation(
                            "create.watch_id",
                            ViolationCode.VIOLATION_CODE_NOT_ALLOWED,
                            "Watch ${create.watchId} is still active on this stream.",
                        ),
                    ),
                )
            }
            if (watches.size >= properties.watch.maxPerStream) {
                throw CoSkyException.watchQuotaExceeded(properties.watch.maxPerStream)
            }
            return permission
        }

        /**
         * Starts a watch that [permissionOf] allowed.
         *
         * @return its job, which ends with the watch.
         */
        fun start(create: WatchRequest.Create, permission: Permission): Job {
            val session = WatchSession(
                create,
                caches.of(create.namespace),
                limits.maxMessageBytes,
                properties.read.minRevisionTimeout.toKotlinDuration(),
                permission,
                caches.metrics,
                send,
            )
            val edge = recordTopology(create)
            // Starts once it is on record, since it can end at once and take itself off.
            val job = scope.launch(start = CoroutineStart.LAZY) {
                session.run()
                watches.remove(create.watchId)
            }
            job.invokeOnCompletion { edge?.close() }
            watches[create.watchId] = ActiveWatch(create, permission, session, job)
            job.start()
            return job
        }

        fun cancel(watchId: Long) {
            watches.remove(watchId)?.job?.cancel()
        }

        /**
         * Sends progress bookmarks, one timer for all the watches, and re-evaluates what the subject may see, until
         * the stream ends.
         *
         * @throws CoSkyException `UNAUTHENTICATED` when the token is no longer valid, which ends the stream.
         */
        suspend fun maintain(): Unit = coroutineScope {
            launch {
                while (true) {
                    delay(properties.watch.progressInterval.toKotlinDuration())
                    watches.forEach { (id, watch) -> send(WatchSession.progress(id, watch.session.cursor.get())) }
                }
            }
            authorizer.changes.collect { reevaluate() }
        }

        /**
         * What a watch delivered was filtered by what its subject could see, so when that changes it has to start
         * over (design §7.3).
         */
        private suspend fun reevaluate() {
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

        /**
         * Only a watch of one service says what its client depends on: one of the whole kind does not (design §6.3).
         */
        private fun recordTopology(create: WatchRequest.Create): AutoCloseable? {
            if (consumer == null || create.kind != Kind.KIND_INSTANCE || create.service.isEmpty()) {
                return null
            }
            return topology?.watching(create.namespace, consumer, create.service)
        }
    }

    private class ActiveWatch(
        val create: WatchRequest.Create,
        val permission: Permission,
        val session: WatchSession,
        val job: Job,
    )
}
