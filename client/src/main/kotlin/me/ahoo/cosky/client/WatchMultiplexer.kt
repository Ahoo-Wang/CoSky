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

package me.ahoo.cosky.client

import io.grpc.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.ahoo.cosky.api.v1.WatchRequest
import me.ahoo.cosky.api.v1.WatchResponse
import me.ahoo.cosky.api.v1.WatchServiceGrpcKt
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.Channel as RequestQueue

/**
 * Carries every watch of a client on one gRPC stream (design §6.5). When the stream fails, views stay as they are; on
 * the next one each watch resumes from its position.
 */
internal class WatchMultiplexer(
    private val connection: Connection,
    private val options: CoSkyClientOptions,
    private val snapshots: SnapshotStore,
    private val scope: CoroutineScope,
) {
    private val watches = ConcurrentHashMap<WatchKey, WatchState>()
    private val byId = ConcurrentHashMap<Long, WatchState>()
    private val ids = AtomicLong()
    private val started = AtomicBoolean()
    private val connectionState = MutableStateFlow(ConnectionState.CONNECTING)

    @Volatile
    private var requests: RequestQueue<WatchRequest>? = null

    val state: StateFlow<ConnectionState>
        get() = connectionState

    /**
     * The same key always shares one watch, which lives as long as the client.
     */
    fun watch(key: WatchKey): WatchState {
        val watch = watches.computeIfAbsent(key) { newKey ->
            val snapshot = snapshots.load(newKey)
            if (snapshot != null) {
                connectionState.compareAndSet(ConnectionState.CONNECTING, ConnectionState.DEGRADED)
            }
            WatchState(newKey, snapshot).also { created -> requests?.let { create(it, created) } }
        }
        if (started.compareAndSet(false, true)) {
            scope.launch { run() }
            scope.launch { resync() }
        }
        return watch
    }

    fun close() {
        connectionState.value = ConnectionState.CLOSED
    }

    private suspend fun run() {
        val backoff = Backoff(options.reconnectMin, options.reconnectMax)
        while (true) {
            val channel = connection.channel()
            try {
                followStream(channel, backoff)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                log.warn("The watch stream failed: {}", e.message)
            }
            requests = null
            connectionState.compareAndSet(ConnectionState.SYNCED, ConnectionState.DEGRADED)
            connectionState.compareAndSet(ConnectionState.CONNECTING, ConnectionState.DEGRADED)
            connection.rotate(channel)
            delay(backoff.next())
        }
    }

    private suspend fun followStream(channel: Channel, backoff: Backoff) {
        val outgoing = RequestQueue<WatchRequest>(RequestQueue.UNLIMITED)
        requests = outgoing
        byId.clear()
        watches.values.forEach { create(outgoing, it) }
        WatchServiceGrpcKt.WatchServiceCoroutineStub(channel).watch(outgoing.receiveAsFlow()).collect { response ->
            backoff.reset()
            connectionState.value = ConnectionState.SYNCED
            handle(outgoing, response)
        }
    }

    private fun handle(outgoing: RequestQueue<WatchRequest>, response: WatchResponse) {
        val watch = byId[response.watchId] ?: return
        when (response.payloadCase) {
            WatchResponse.PayloadCase.GONE -> restart(outgoing, response.watchId, watch)
            WatchResponse.PayloadCase.CANCELED -> retryLater(outgoing, response, watch)
            else -> if (watch.apply(response)) {
                snapshots.schedule(watch.key, watch::toSnapshot)
            }
        }
    }

    private fun retryLater(outgoing: RequestQueue<WatchRequest>, response: WatchResponse, watch: WatchState) {
        log.warn("Watch {} was canceled: {}", watch.key, response.canceled.message)
        byId.remove(response.watchId)
        scope.launch {
            delay(CANCELED_RETRY)
            if (requests === outgoing) {
                restart(outgoing, response.watchId, watch)
            }
        }
    }

    private fun restart(outgoing: RequestQueue<WatchRequest>, oldId: Long, watch: WatchState) {
        byId.remove(oldId)
        watch.startOver()
        create(outgoing, watch)
    }

    private fun create(outgoing: RequestQueue<WatchRequest>, watch: WatchState) {
        val id = ids.incrementAndGet()
        byId[id] = watch
        val create = WatchRequest.Create.newBuilder()
            .setWatchId(id)
            .setNamespace(watch.key.namespace)
            .setKind(watch.key.kind)
            .setName(watch.key.name)
            .setService(watch.key.service)
        if (watch.hasPosition) {
            create.setFromRevision(watch.revision).setFromEpoch(watch.epoch)
        }
        outgoing.trySend(WatchRequest.newBuilder().setCreate(create).build())
    }

    /**
     * Starts every watch over now and then: a backstop for anything an incremental path could miss (design §6.4).
     */
    private suspend fun resync() {
        while (true) {
            delay(options.resyncInterval * (RESYNC_JITTER_FLOOR + Random.nextDouble() * RESYNC_JITTER_SPREAD))
            val outgoing = requests ?: continue
            byId.toMap().forEach { (id, watch) ->
                outgoing.trySend(cancelOf(id))
                restart(outgoing, id, watch)
            }
        }
    }

    private fun cancelOf(id: Long): WatchRequest =
        WatchRequest.newBuilder().setCancel(WatchRequest.Cancel.newBuilder().setWatchId(id)).build()

    private companion object {
        val log = LoggerFactory.getLogger(WatchMultiplexer::class.java)
        val CANCELED_RETRY = 5.seconds
        const val RESYNC_JITTER_FLOOR = 0.8
        const val RESYNC_JITTER_SPREAD = 0.4
    }
}
