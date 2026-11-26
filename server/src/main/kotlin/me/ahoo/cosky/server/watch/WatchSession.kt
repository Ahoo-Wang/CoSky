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

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.kind
import me.ahoo.cosky.api.v1.GoneReason
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.WatchRequest
import me.ahoo.cosky.api.v1.WatchResponse
import me.ahoo.cosky.server.auth.Permission
import me.ahoo.cosky.server.auth.authorizedName
import me.ahoo.cosky.server.observe.CoSkyMetrics
import me.ahoo.cosky.store.Change
import me.ahoo.cosky.store.ChangeBatch
import me.ahoo.cosky.store.ChangeType
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration

/**
 * One watch of a stream (design §7.3): starts from a snapshot or a position, then delivers whole revisions in order,
 * split by size (§4.6).
 *
 * @param send delivers a response; it suspends while the stream is full, which holds this watch back only.
 */
class WatchSession(
    private val create: WatchRequest.Create,
    private val cache: NamespaceCache,
    private val maxMessageBytes: Int,
    private val catchUpTimeout: Duration,
    private val permission: Permission = Permission.All,
    private val metrics: CoSkyMetrics = CoSkyMetrics(),
    private val send: suspend (WatchResponse) -> Unit,
) {
    /**
     * The revision delivered so far, for progress bookmarks.
     */
    val cursor = AtomicLong(create.fromRevision)

    /**
     * @throws CoSkyException with `UNAVAILABLE` when this replica stays behind the client, which ends the whole stream
     * and sends the client to another endpoint (design D29).
     */
    suspend fun run() {
        metrics.watchStarted()
        try {
            serve()
        } finally {
            metrics.watchEnded()
        }
    }

    private suspend fun serve() {
        val start = if (create.fromRevision == 0L) firstView() else firstViewToResumeOn()
        when {
            start.isClosed -> sendClosed()

            create.fromRevision == 0L -> {
                sendSnapshot(start)
                cursor.set(start.revision)
                follow(start.epoch)
            }

            create.fromEpoch != start.epoch -> sendGone(start, GoneReason.GONE_REASON_EPOCH)

            create.fromRevision < start.earliest -> sendGone(start, GoneReason.GONE_REASON_COMPACTED)

            create.fromRevision <= start.revision || catchUp(start) -> follow(start.epoch, announce = true)
        }
    }

    /**
     * A client with nothing to go on waits as long as loading the namespace takes, however large, but not for a store
     * this replica cannot read: it goes to another replica, which may well serve it (#1264).
     */
    private suspend fun firstView(): NamespaceView = cache.awaitLoaded() ?: throw cannotLoad()

    /**
     * A client with a position got it from some replica, which may well serve it now: one that has not loaded the
     * namespace within the catch-up timeout sends it elsewhere rather than keep it waiting (design D29).
     */
    private suspend fun firstViewToResumeOn(): NamespaceView =
        withTimeoutOrNull(catchUpTimeout) { cache.awaitLoaded() ?: throw cannotLoad() }
            ?: throw CoSkyException.replicaNotReady(
                create.namespace,
                "This replica has not loaded namespace ${create.namespace} yet.",
            )

    private fun cannotLoad(): CoSkyException = CoSkyException.replicaNotReady(
        create.namespace,
        "This replica cannot load namespace ${create.namespace} from its store.",
    )

    /**
     * Waits for this replica to reach the position of the client, which came from another replica.
     *
     * @return false if the position turned out to be invalid.
     */
    private suspend fun catchUp(start: NamespaceView): Boolean {
        val caughtUp = withTimeoutOrNull(catchUpTimeout) {
            cache.views.first { it != null && (it.epoch != start.epoch || it.revision >= create.fromRevision) }
        }
        if (caughtUp != null) {
            return true
        }
        val stored = cache.storePosition()
        if (stored?.epoch == start.epoch && stored.revision >= create.fromRevision) {
            throw CoSkyException.replicaNotReady(
                create.namespace,
                "This replica is behind revision ${create.fromRevision} of namespace ${create.namespace}.",
            )
        }
        // The store does not have it either: it comes from a timeline that was lost without anyone noticing (§6.4).
        // Waiting for another replica would not help, and the client would reconnect forever.
        sendGone(start, GoneReason.GONE_REASON_REGRESSION)
        return false
    }

    /**
     * @param announce whether to send the position once caught up, as a watch resumed from a client's snapshot has
     * nothing else to tell it that what it holds is current; it would otherwise wait for the next bookmark (§7.3).
     */
    private suspend fun follow(epoch: String, announce: Boolean = false) {
        var unannounced = announce
        cache.views.first { view ->
            when {
                view == null -> false

                view.isClosed -> {
                    sendClosed()
                    true
                }

                view.epoch != epoch -> {
                    sendGone(view, GoneReason.GONE_REASON_EPOCH)
                    true
                }

                cursor.get() < view.earliest -> {
                    sendGone(view, GoneReason.GONE_REASON_SLOW)
                    true
                }

                else -> {
                    deliver(view)
                    if (unannounced) {
                        unannounced = false
                        send(progress(create.watchId, cursor.get()))
                    }
                    false
                }
            }
        }
    }

    private suspend fun sendClosed() {
        send(canceled(create.watchId, CoSkyException.namespaceNotFound(create.namespace)))
    }

    private suspend fun deliver(view: NamespaceView) {
        view.window.takeLastWhile { it.revision > cursor.get() }.forEach { batch ->
            sendEvents(batch)
            cursor.set(batch.revision)
        }
    }

    private suspend fun sendSnapshot(view: NamespaceView) {
        val items = candidates(view).filter { matches(it) }
        val chunks = items.chunkedByBytes(maxMessageBytes) { it.serializedSize }.ifEmpty { listOf(emptyList()) }
        chunks.forEachIndexed { index, chunk ->
            send(
                response {
                    snapshot = WatchResponse.Snapshot.newBuilder()
                        .addAllItems(chunk)
                        .setRevision(view.revision)
                        .setEpoch(view.epoch)
                        .setMore(index < chunks.lastIndex)
                        .build()
                },
            )
        }
    }

    private suspend fun sendEvents(batch: ChangeBatch) {
        val changes = batch.changes.filter { matches(it.resource) }
        val chunks = changes.chunkedByBytes(maxMessageBytes) { it.resource.serializedSize }
        metrics.eventsSent(changes.size)
        chunks.forEachIndexed { index, chunk ->
            send(
                response {
                    events = WatchResponse.Events.newBuilder()
                        .setRevision(batch.revision)
                        .addAllEvents(chunk.map { event(it) })
                        .setMore(index < chunks.lastIndex)
                        .build()
                },
            )
        }
    }

    private fun event(change: Change): WatchResponse.Event = WatchResponse.Event.newBuilder()
        .setType(
            when (change.type) {
                ChangeType.PUT -> WatchResponse.Event.Type.TYPE_PUT
                ChangeType.DELETE -> WatchResponse.Event.Type.TYPE_DELETE
            },
        )
        .setResource(change.resource)
        .build()

    /**
     * Where to look, so that the size of the namespace does not decide what a watch of one object costs.
     */
    private fun candidates(view: NamespaceView): Collection<Resource> = when {
        create.name.isNotEmpty() -> listOfNotNull(view.objects[keyOf(create.name)])
        create.kind == Kind.KIND_INSTANCE && create.service.isNotEmpty() -> view.instancesOf(create.service)
        else -> view.objects.values
    }

    private fun keyOf(name: String): ResourceKey = ResourceKey.newBuilder()
        .setNamespace(create.namespace)
        .setKind(create.kind)
        .setName(name)
        .build()

    private fun matches(resource: Resource): Boolean =
        resource.kind == create.kind &&
            (create.name.isEmpty() || resource.meta.name == create.name) &&
            (create.service.isEmpty() || isInstanceOf(resource, create.service)) &&
            permission.allows(resource.authorizedName)

    private fun isInstanceOf(resource: Resource, service: String): Boolean =
        create.kind == Kind.KIND_INSTANCE && resource.instance.service == service

    private suspend fun sendGone(view: NamespaceView, reason: GoneReason) {
        metrics.gone(reason)
        send(gone(create.watchId, view.earliest, view.epoch, reason))
    }

    private inline fun response(block: WatchResponse.Builder.() -> Unit): WatchResponse =
        WatchResponse.newBuilder().setWatchId(create.watchId).apply(block).build()

    companion object {
        fun canceled(watchId: Long, error: CoSkyException): WatchResponse = WatchResponse.newBuilder()
            .setWatchId(watchId)
            .setCanceled(error.toWatchCanceled())
            .build()

        /**
         * @param earliest the earliest revision [epoch] can be resumed from.
         */
        fun gone(watchId: Long, earliest: Long, epoch: String, reason: GoneReason): WatchResponse =
            WatchResponse.newBuilder()
                .setWatchId(watchId)
                .setPositionGone(
                    WatchResponse.Gone.newBuilder()
                        .setEarliestRevision(earliest)
                        .setEpoch(epoch)
                        .setReason(reason),
                )
                .build()

        fun progress(watchId: Long, revision: Long): WatchResponse = WatchResponse.newBuilder()
            .setWatchId(watchId)
            .setProgress(revision)
            .build()
    }
}

/**
 * Splits into lists of at most [maxBytes], each holding at least one item. Keeps the order.
 */
internal inline fun <T> List<T>.chunkedByBytes(maxBytes: Int, size: (T) -> Int): List<List<T>> {
    val chunks = mutableListOf<List<T>>()
    var current = mutableListOf<T>()
    var bytes = 0
    forEach { item ->
        val itemBytes = size(item)
        if (current.isNotEmpty() && bytes + itemBytes > maxBytes) {
            chunks += current
            current = mutableListOf()
            bytes = 0
        }
        current += item
        bytes += itemBytes
    }
    if (current.isNotEmpty()) {
        chunks += current
    }
    return chunks
}
