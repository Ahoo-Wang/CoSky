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
import me.ahoo.cosky.api.error.CoSkyException
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

    /** The keys [watch] returned a watch for, which lives as long as the client. */
    private val kept = ConcurrentHashMap.newKeySet<WatchKey>()

    /** How many holds each watch [hold] returned has, under [lock]. */
    private val holds = HashMap<WatchKey, Int>()
    private val byId = ConcurrentHashMap<Long, WatchState>()
    private val ids = AtomicLong()
    private val started = AtomicBoolean()
    private val connectionState = MutableStateFlow(ConnectionState.CONNECTING)

    /**
     * Held whenever the stream's queue and the routes from watch ids change, so that both belong to one stream: while
     * a new stream takes over and creates every watch, while a watch is added, and while one starts over. A watch
     * added during a takeover would otherwise be created twice on the stream, taking up the server's quota for
     * nothing; one started over for a stream that was replaced meanwhile would lose its route on the new one.
     */
    private val lock = Any()

    @Volatile
    private var requests: RequestQueue<WatchRequest>? = null

    val state: StateFlow<ConnectionState>
        get() = connectionState

    /**
     * The same key always shares one watch. Once returned from here, it lives as long as the client: a view read by its
     * value alone is not subscribed to, so nothing could tell when it is no longer needed.
     */
    fun watch(key: WatchKey): WatchState = watches[key]?.takeIf { key in kept } ?: use(key) { kept += key }

    /**
     * The same watch as [watch], for a key followed only for a while: once [release] has been called for every hold,
     * and [watch] was never called for the key, the watch ends, on the server too, where it counts against the
     * stream's quota.
     */
    fun hold(key: WatchKey): WatchState = use(key) { holds.merge(key, 1, Int::plus) }

    fun release(key: WatchKey) = synchronized(lock) {
        val left = checkNotNull(holds[key]) { "Watch $key is not held." } - 1
        if (left > 0) {
            holds[key] = left
        } else {
            holds -= key
            if (key !in kept) {
                watches.remove(key)?.let { end(it) }
            }
        }
    }

    /**
     * Reads the snapshot before taking the lock, since that reads a file. Losing a race only wastes the read, or, for a
     * key released meanwhile, starts its watch without one.
     */
    private fun use(key: WatchKey, mark: () -> Unit): WatchState {
        val snapshot = if (watches.containsKey(key)) null else snapshots.load(key)
        val watch = synchronized(lock) {
            watches.getOrPut(key) {
                if (snapshot != null) {
                    connectionState.compareAndSet(ConnectionState.CONNECTING, ConnectionState.DEGRADED)
                }
                WatchState(key, snapshot).also { created -> requests?.let { create(it, created) } }
            }.also { mark() }
        }
        if (started.compareAndSet(false, true)) {
            scope.launch { run() }
            scope.launch { resync() }
        }
        return watch
    }

    /**
     * Ends a watch no longer held, on the stream it is on. Its responses still on the way find no route.
     */
    private fun end(watch: WatchState) {
        byId.filterValues { it === watch }.keys.forEach { id ->
            byId -= id
            requests?.trySend(cancelOf(id))
        }
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
                log.warn("The watch stream to {} failed: {}", channel.authority(), describe(e))
            }
            synchronized(lock) { requests = null }
            connectionState.compareAndSet(ConnectionState.SYNCED, ConnectionState.DEGRADED)
            connectionState.compareAndSet(ConnectionState.CONNECTING, ConnectionState.DEGRADED)
            connection.rotate(channel)
            delay(backoff.next())
        }
    }

    private suspend fun followStream(channel: Channel, backoff: Backoff) {
        val outgoing = RequestQueue<WatchRequest>(RequestQueue.UNLIMITED)
        synchronized(lock) {
            requests = outgoing
            byId.clear()
            // Each forgets what the broken stream delivered only part of as it is created anew.
            watches.values.forEach { create(outgoing, it) }
        }
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
        CoSkyException.from(response.canceled)?.let { watch.refused(it) }
        byId.remove(response.watchId)
        scope.launch {
            delay(CANCELED_RETRY)
            synchronized(lock) {
                // Unless the watch ended meanwhile.
                if (requests === outgoing && watches[watch.key] === watch) {
                    recreate(outgoing, watch)
                }
            }
        }
    }

    /**
     * Starts a watch over, as long as [outgoing] is still the stream's and [oldId] still routes to the watch: a stream
     * that took over meanwhile created every watch afresh, and one started over already has a new id.
     */
    private fun restart(outgoing: RequestQueue<WatchRequest>, oldId: Long, watch: WatchState) = synchronized(lock) {
        if (requests === outgoing && byId.remove(oldId, watch)) {
            recreate(outgoing, watch)
        }
    }

    private fun recreate(outgoing: RequestQueue<WatchRequest>, watch: WatchState) =
        create(outgoing, watch, startOver = true)

    /**
     * Creates [watch] under a new id, the only one it takes responses to from then on: those to an id it had before can
     * still be on their way, and the stream's coroutine may have found the watch by one just before it changed.
     */
    private fun create(outgoing: RequestQueue<WatchRequest>, watch: WatchState, startOver: Boolean = false) {
        val id = ids.incrementAndGet()
        if (startOver) watch.startOver(id) else watch.createdAs(id)
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
            // The queue and the routes are read together, or they could belong to different streams.
            synchronized(lock) {
                val outgoing = requests ?: return@synchronized
                byId.toMap().forEach { (id, watch) ->
                    outgoing.trySend(cancelOf(id))
                    restart(outgoing, id, watch)
                }
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
