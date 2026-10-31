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
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.ListResponse
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.WatchResponse
import java.util.concurrent.atomic.AtomicLong

internal data class WatchKey(val namespace: String, val kind: Kind, val name: String = "", val service: String = "")

/**
 * The local view of one watch. It applies whole revisions only, and keeps serving the old view while a new one arrives
 * (design §7.5). One coroutine at a time applies responses.
 */
internal class WatchState(val key: WatchKey, snapshot: ListResponse?) {
    private val objects = MutableStateFlow(snapshot?.itemsList.orEmpty().associateBy { it.meta.name })
    private var pendingSnapshot = mutableListOf<Resource>()
    private var pendingEvents = mutableListOf<WatchResponse.Event>()
    private var resyncEpoch = ""
    private var resyncRevision = -1L

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

    fun toSnapshot(): ListResponse = ListResponse.newBuilder()
        .addAllItems(objects.value.values)
        .setEpoch(epoch)
        .setRevision(revision)
        .build()

    /**
     * Forgets the position, so that the next watch starts from a snapshot; the view stays until that arrives.
     */
    fun startOver() {
        resyncEpoch = epoch
        resyncRevision = revision
        epoch = ""
        revision = 0
        pendingSnapshot = mutableListOf()
        pendingEvents = mutableListOf()
    }

    /**
     * Applies one response.
     *
     * @return whether the view or the position changed.
     */
    fun apply(response: WatchResponse): Boolean = when (response.payloadCase) {
        WatchResponse.PayloadCase.SNAPSHOT -> applySnapshot(response.snapshot)
        WatchResponse.PayloadCase.EVENTS -> applyEvents(response.events)
        WatchResponse.PayloadCase.PROGRESS -> applyProgress(response.progress)
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
        objects.value = batch.fold(objects.value) { current, event ->
            val name = event.resource.meta.name
            when (event.type) {
                WatchResponse.Event.Type.TYPE_DELETE -> current - name
                else -> current + (name to event.resource)
            }
        }
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
}
