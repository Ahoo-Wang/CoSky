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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.ListResponse
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.WatchResponse
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds

internal data class WatchKey(val namespace: String, val kind: Kind, val name: String = "", val service: String = "")

/**
 * The local view of one watch. It applies whole revisions only, and keeps serving the old view while a new one arrives
 * (design §7.5). Responses arrive on the stream's coroutine while a periodic resync can start the watch over from
 * another, hence the synchronization.
 *
 * @param streamFailures how many times the stream that carries it has ended so far, which a view known only from a
 * snapshot stops waiting for the server's answer on; none for a view that is not waited on.
 */
internal class WatchState(
    val key: WatchKey,
    snapshot: ListResponse?,
    private val streamFailures: StateFlow<Long>? = null,
) {
    private val objects = MutableStateFlow(snapshot?.itemsList.orEmpty().associateBy { it.meta.name })
    private var pendingSnapshot = mutableListOf<Resource>()
    private var pendingEvents = mutableListOf<WatchResponse.Event>()
    private var resyncEpoch = ""
    private var resyncRevision = -1L

    /** The id the watch was last created with on the stream; until then 0, which the stream never hands out. */
    private var watchId = 0L
    private val knownState = MutableStateFlow(snapshot != null)

    /** Whether the server has answered for this watch, with a snapshot, events or a bookmark. */
    private val answered = MutableStateFlow(false)
    private val refusal = MutableStateFlow<CoSkyException?>(null)

    /**
     * Whether the view reflects a server, now or as saved in a snapshot. Until then an empty view only means not
     * knowing yet, rather than nothing being there.
     */
    val known: StateFlow<Boolean>
        get() = knownState

    /**
     * Waits until the view is known. A server refusing the watch is an answer too, as long as nothing else is known:
     * waiting on would only turn the reason into a timeout.
     *
     * @throws CoSkyException what the server refused it with.
     */
    suspend fun awaitKnown() {
        knownState.combine(refusal) { known, refused -> known || refused != null }.first { it }
        if (!knownState.value) {
            refusal.value?.let { throw it }
        }
        awaitServer()
    }

    /**
     * A view known only from a snapshot waits a moment for the server to answer: a snapshot that is out of date, or
     * from another server at the same address, would otherwise be read as the truth, such as no instances where there
     * are some. It stops waiting once the stream ends, as when no server answers: the snapshot is what there is then.
     */
    private suspend fun awaitServer() {
        val failures = streamFailures ?: return
        val before = failures.value
        withTimeoutOrNull(SERVER_WAIT) {
            combine(answered, failures, refusal) { answered, now, refused ->
                answered || now != before || refused != null
            }.first { it }
        }
    }

    /**
     * The server ended this watch. It is tried again, since permissions and namespaces change.
     */
    fun refused(error: CoSkyException) {
        refusal.value = error
    }

    @Volatile
    var epoch: String = snapshot?.epoch.orEmpty()
        private set

    @Volatile
    var revision: Long = snapshot?.revision ?: 0
        private set

    /**
     * Times a fresh snapshot disagreed with a view at the same position: the incremental path missed something, which
     * should never happen.
     */
    val drift = AtomicLong()

    val view: StateFlow<Map<String, Resource>>
        get() = objects

    val hasPosition: Boolean
        get() = epoch.isNotEmpty()

    /**
     * The view and its position from one moment, as [apply] leaves them: objects older than the position would make a
     * restart resume past changes they lack. Only references are taken under the lock, since a view never changes once
     * published.
     */
    fun toSnapshot(): ListResponse {
        val snapshot = ListResponse.newBuilder()
        val items = synchronized(this) {
            snapshot.setEpoch(epoch).setRevision(revision)
            objects.value.values
        }
        return snapshot.addAllItems(items).build()
    }

    /**
     * Takes the responses to [id] from now on, and only those, and forgets what arrived only in part before. The
     * stream's coroutine may have found this watch by an id it had before just as it is created anew, and responses
     * to that id are still on their way: a chunk of the snapshot it was starting over from would end up in the next
     * one, and part of a revision under a later one.
     */
    @Synchronized
    fun createdAs(id: Long) {
        watchId = id
        discardPartial()
    }

    /**
     * Forgets the position, so that the next watch, created as [id], starts from a snapshot; the view stays until that
     * arrives. In one step, so that nothing the watch had before slips in once its position is gone.
     */
    @Synchronized
    fun startOver(id: Long) {
        resyncEpoch = epoch
        resyncRevision = revision
        epoch = ""
        revision = 0
        createdAs(id)
    }

    /**
     * Forgets what a broken stream delivered only part of. The next stream sends it whole, and an object in an old part
     * may have been deleted since: nothing would remove it again.
     */
    private fun discardPartial() {
        pendingSnapshot = mutableListOf()
        pendingEvents = mutableListOf()
    }

    /**
     * Applies one response, unless it is to an id the watch had before [createdAs].
     *
     * @return whether the view or the position changed.
     */
    @Synchronized
    fun apply(response: WatchResponse): Boolean = if (response.watchId != watchId) {
        false
    } else {
        applyCurrent(response)
    }

    private fun applyCurrent(response: WatchResponse): Boolean {
        val changed = when (response.payloadCase) {
            WatchResponse.PayloadCase.SNAPSHOT -> applySnapshot(response.snapshot)
            WatchResponse.PayloadCase.EVENTS -> applyEvents(response.events)
            WatchResponse.PayloadCase.PROGRESS -> applyProgress(response.progress)
            else -> false
        }
        if (isWhole(response)) {
            answered.value = true
        }
        return changed
    }

    /** The last message of a snapshot or of a revision's events, or a bookmark: what makes the view current. */
    private fun isWhole(response: WatchResponse): Boolean = when (response.payloadCase) {
        WatchResponse.PayloadCase.SNAPSHOT -> !response.snapshot.more
        WatchResponse.PayloadCase.EVENTS -> !response.events.more
        WatchResponse.PayloadCase.PROGRESS -> true
        else -> false
    }

    private fun applySnapshot(snapshot: WatchResponse.Snapshot): Boolean {
        pendingSnapshot += snapshot.itemsList
        if (snapshot.more) {
            return false
        }
        val fresh = pendingSnapshot.associateBy { it.meta.name }
        pendingSnapshot = mutableListOf()
        if (resyncEpoch == snapshot.epoch && resyncRevision == snapshot.revision && fresh != objects.value) {
            drift.incrementAndGet()
        }
        objects.value = fresh
        epoch = snapshot.epoch
        revision = snapshot.revision
        refusal.value = null
        knownState.value = true
        return true
    }

    private fun applyEvents(events: WatchResponse.Events): Boolean {
        pendingEvents += events.eventsList
        if (events.more) {
            return false
        }
        val batch = pendingEvents
        pendingEvents = mutableListOf()
        // Across a reconnect delivery is at least once, so what was applied already is dropped (design §7.3).
        if (events.revision <= revision) {
            return false
        }
        val applied = objects.value.toMutableMap()
        batch.forEach { event ->
            val name = event.resource.meta.name
            // A snapshot may hold objects newer than its own revision (design §6.1), and the events after it replay
            // what they already have: applying those would move an object back for a while (§4.6).
            if ((applied[name]?.meta?.revision ?: 0) < events.revision) {
                when (event.type) {
                    WatchResponse.Event.Type.TYPE_DELETE -> applied.remove(name)
                    else -> applied[name] = event.resource
                }
            }
        }
        objects.value = applied
        revision = events.revision
        return true
    }

    private fun applyProgress(progress: Long): Boolean {
        if (progress <= revision) {
            return false
        }
        revision = progress
        return true
    }

    private companion object {
        /** How long a view known only from a snapshot waits for the server; a server that is there answers in less. */
        val SERVER_WAIT = 1.seconds
    }
}
