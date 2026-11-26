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

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.GoneReason
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.ListResponse
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.WatchResponse
import me.ahoo.cosky.api.v1.WatchResponse.Event.Type.TYPE_DELETE
import me.ahoo.cosky.api.v1.WatchResponse.Event.Type.TYPE_PUT
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class WatchStateTest {
    private val state = WatchState(WatchKey("prod", Kind.KIND_CONFIG), null)

    private fun config(name: String): Resource = resource { meta = objectMeta { this.name = name } }

    private fun snapshot(
        vararg names: String,
        revision: Long = 1,
        epoch: String = "e",
        more: Boolean = false,
        id: Long = 0,
    ) = WatchResponse.newBuilder().setWatchId(id).setSnapshot(
        WatchResponse.Snapshot.newBuilder()
            .addAllItems(names.map { config(it) })
            .setRevision(revision)
            .setEpoch(epoch)
            .setMore(more),
    ).build()

    private fun events(
        revision: Long,
        type: WatchResponse.Event.Type,
        name: String,
        more: Boolean = false,
        id: Long = 0,
    ) = WatchResponse.newBuilder().setWatchId(id).setEvents(
        WatchResponse.Events.newBuilder()
            .setRevision(revision)
            .setMore(more)
            .addEvents(WatchResponse.Event.newBuilder().setType(type).setResource(config(name))),
    ).build()

    private fun put(revision: Long, name: String, more: Boolean = false, id: Long = 0) =
        events(revision, WatchResponse.Event.Type.TYPE_PUT, name, more, id)

    private fun names(): Set<String> = state.view.value.keys

    /**
     * An object as it was written at [revision], with content that says so.
     */
    private fun version(name: String, revision: Long): Resource = resource {
        meta = objectMeta {
            this.name = name
            this.revision = revision
        }
        config = configSpec { content = "v$revision" }
    }

    private fun snapshotOf(vararg items: Resource, revision: Long, more: Boolean = false) =
        WatchResponse.newBuilder().setSnapshot(
            WatchResponse.Snapshot.newBuilder()
                .addAllItems(items.toList())
                .setRevision(revision)
                .setEpoch("e")
                .setMore(more),
        ).build()

    private fun change(revision: Long, vararg events: Pair<WatchResponse.Event.Type, Resource>) =
        WatchResponse.newBuilder().setEvents(
            WatchResponse.Events.newBuilder()
                .setRevision(revision)
                .addAllEvents(
                    events.map { (type, resource) ->
                        WatchResponse.Event.newBuilder().setType(type).setResource(resource).build()
                    },
                ),
        ).build()

    private fun contents(): Map<String, String> = state.view.value.mapValues { it.value.config.content }

    /**
     * Every view a subscriber gets while [responses] arrive one by one, as name to content. A state flow passes on
     * changes only, so a response that leaves the view as it was shows nothing.
     */
    private fun viewsSeen(vararg responses: WatchResponse): List<Map<String, String>> {
        val seen = mutableListOf(contents())
        responses.forEach { response ->
            state.apply(response)
            contents().takeIf { it != seen.last() }?.let { seen += it }
        }
        return seen
    }

    @Test
    fun `a refused watch is an answer until something is known`() = runBlocking<Unit> {
        val refusal = CoSkyException.permissionDenied("not for you")
        state.refused(refusal)
        runCatching { state.awaitKnown() }.exceptionOrNull().assert().isSameAs(refusal)
        state.apply(snapshot("a"))
        state.awaitKnown()
        state.refused(refusal)
        state.awaitKnown()
    }

    @Test
    fun `a snapshot on disk outweighs a refusal`() = runBlocking<Unit> {
        val restored = WatchState(WatchKey("prod", Kind.KIND_CONFIG), ListResponse.getDefaultInstance())
        restored.refused(CoSkyException.permissionDenied("not for you"))
        restored.awaitKnown()
    }

    private fun restored(ends: MutableStateFlow<Long>): WatchState = WatchState(
        WatchKey("prod", Kind.KIND_CONFIG),
        ListResponse.newBuilder().addItems(config("saved")).setEpoch("e").setRevision(1).build(),
        ends,
    ).also { it.createdAs(1) }

    @Test
    fun `a view known only from a snapshot waits for the server to answer`() = runBlocking<Unit> {
        val restored = restored(MutableStateFlow(0L))
        val loading = async { restored.awaitKnown() }
        delay(100.milliseconds)
        loading.isCompleted.assert().isFalse()
        restored.apply(WatchResponse.newBuilder().setWatchId(1).setProgress(1).build())
        withTimeout(500.milliseconds) { loading.await() }
    }

    @Test
    fun `a view known only from a snapshot stops waiting once the stream ends, as with no server`() =
        runBlocking<Unit> {
            val ends = MutableStateFlow(0L)
            val restored = restored(ends)
            val loading = async { restored.awaitKnown() }
            delay(100.milliseconds)
            ends.value = 1
            withTimeout(500.milliseconds) { loading.await() }
            restored.view.value.keys.assert().containsExactly("saved")
        }

    @Test
    fun `a view known only from a snapshot waits no longer than a moment`() = runBlocking<Unit> {
        val restored = restored(MutableStateFlow(0L))
        withTimeout(3.seconds) { restored.awaitKnown() }
        restored.view.value.keys.assert().containsExactly("saved")
    }

    @Test
    fun `a new watch knows nothing`() {
        state.hasPosition.assert().isFalse()
        names().assert().isEmpty()
    }

    @Test
    fun `a snapshot on disk gives the view and the position`() {
        val restored = WatchState(
            WatchKey("prod", Kind.KIND_CONFIG),
            ListResponse.newBuilder().addItems(config("a")).setEpoch("e").setRevision(7).build(),
        )
        restored.view.value.keys.assert().containsExactly("a")
        restored.hasPosition.assert().isTrue()
        restored.revision.assert().isEqualTo(7)
        restored.toSnapshot().itemsList.map { it.meta.name }.assert().containsExactly("a")
    }

    @Test
    fun `a snapshot in chunks takes effect with the last one`() {
        state.apply(snapshot("a", more = true)).assert().isFalse()
        names().assert().isEmpty()
        state.apply(snapshot("b")).assert().isTrue()
        names().assert().containsExactlyInAnyOrder("a", "b")
        state.epoch.assert().isEqualTo("e")
    }

    @Test
    fun `what a broken stream left of a snapshot does not reach the next one`() {
        state.apply(snapshot("deleted since", more = true)).assert().isFalse()
        // Created on the next stream.
        state.createdAs(1)
        state.apply(snapshot("kept", id = 1)).assert().isTrue()
        names().assert().containsExactly("kept")
    }

    @Test
    fun `the rest of a snapshot to an id the watch had before does not reach the next one`() {
        state.createdAs(1)
        state.apply(snapshot("deleted since", more = true, id = 1)).assert().isFalse()
        state.startOver(2)
        state.apply(snapshot("ghost", more = true, id = 1)).assert().isFalse()
        state.apply(snapshot("stale", revision = 1, id = 1)).assert().isFalse()
        state.hasPosition.assert().isFalse()
        state.apply(snapshot("kept", revision = 2, id = 2)).assert().isTrue()
        names().assert().containsExactly("kept")
    }

    @Test
    fun `the rest of a revision to an id the watch had before is not applied under a later one`() {
        state.createdAs(1)
        state.apply(snapshot("a", revision = 1, id = 1))
        state.startOver(2)
        state.apply(put(2, "ghost", more = true, id = 1)).assert().isFalse()
        state.apply(snapshot("a", revision = 2, id = 2)).assert().isTrue()
        state.apply(put(3, "b", id = 2)).assert().isTrue()
        names().assert().containsExactlyInAnyOrder("a", "b")
        state.revision.assert().isEqualTo(3)
    }

    @Test
    fun `a revision in chunks takes effect with the last one`() {
        state.apply(snapshot("a"))
        state.apply(put(2, "b", more = true)).assert().isFalse()
        names().assert().containsExactly("a")
        state.revision.assert().isEqualTo(1)
        state.apply(put(2, "c")).assert().isTrue()
        names().assert().containsExactlyInAnyOrder("a", "b", "c")
        state.revision.assert().isEqualTo(2)
    }

    @Test
    fun `a delete removes the object`() {
        state.apply(snapshot("a", "b"))
        state.apply(events(2, WatchResponse.Event.Type.TYPE_DELETE, "a"))
        names().assert().containsExactly("b")
    }

    @Test
    fun `a revision already applied is dropped`() {
        state.apply(snapshot("a", revision = 5))
        state.apply(put(5, "again")).assert().isFalse()
        state.apply(put(4, "older")).assert().isFalse()
        names().assert().containsExactly("a")
    }

    @Test
    fun `events a snapshot already has do not move its newer objects back`() {
        viewsSeen(
            snapshotOf(version("a", 3), revision = 1),
            change(2, TYPE_PUT to version("a", 2)),
            change(3, TYPE_PUT to version("a", 3)),
        ).assert().containsExactly(emptyMap(), mapOf("a" to "v3"))
        state.revision.assert().isEqualTo(3)
    }

    @Test
    fun `a delete older than the object a snapshot holds leaves it`() {
        // Deleted at 2 and created again at 3, which the snapshot at 1 has already read.
        viewsSeen(
            snapshotOf(version("a", 3), revision = 1),
            change(2, TYPE_DELETE to version("a", 2)),
            change(3, TYPE_PUT to version("a", 3)),
        ).assert().containsExactly(emptyMap(), mapOf("a" to "v3"))
    }

    @Test
    fun `a name deleted and then created again comes back`() {
        viewsSeen(
            snapshotOf(version("a", 1), revision = 1),
            change(2, TYPE_DELETE to version("a", 2)),
            change(3, TYPE_PUT to version("a", 3)),
        ).assert().containsExactly(emptyMap(), mapOf("a" to "v1"), emptyMap(), mapOf("a" to "v3"))
    }

    @Test
    fun `events overlapping a snapshot in chunks apply only to what they are newer than`() {
        viewsSeen(
            snapshotOf(version("a", 4), revision = 2, more = true),
            snapshotOf(version("b", 1), version("c", 2), revision = 2),
            change(3, TYPE_PUT to version("a", 3), TYPE_PUT to version("b", 3)),
            change(4, TYPE_PUT to version("a", 4)),
            change(5, TYPE_DELETE to version("c", 5)),
        ).assert().containsExactly(
            emptyMap(),
            mapOf("a" to "v4", "b" to "v1", "c" to "v2"),
            mapOf("a" to "v4", "b" to "v3", "c" to "v2"),
            mapOf("a" to "v4", "b" to "v3"),
        )
        state.revision.assert().isEqualTo(5)
    }

    @Test
    fun `a snapshot taken while revisions arrive holds the objects of its own revision`() {
        val names = List(OBJECTS) { "config-$it" }
        state.apply(snapshotOf(*names.map { version(it, 1) }.toTypedArray(), revision = 1))
        val applying = thread {
            (2L..REVISIONS).forEach { revision ->
                state.apply(change(revision, *names.map { TYPE_PUT to version(it, revision) }.toTypedArray()))
            }
        }
        val torn = mutableListOf<Long>()
        while (applying.isAlive) {
            val saved = state.toSnapshot()
            if (saved.itemsList.any { it.meta.revision != saved.revision }) {
                torn += saved.revision
            }
        }
        applying.join()
        torn.assert().isEmpty()
        state.toSnapshot().itemsList.assert().allMatch { it.meta.revision == REVISIONS }
    }

    @Test
    fun `a bookmark only moves forward`() {
        state.apply(snapshot("a", revision = 5))
        state.apply(WatchResponse.newBuilder().setProgress(9).build()).assert().isTrue()
        state.revision.assert().isEqualTo(9)
        state.apply(WatchResponse.newBuilder().setProgress(3).build()).assert().isFalse()
        state.revision.assert().isEqualTo(9)
    }

    @Test
    fun `other responses change nothing`() {
        val gone = WatchResponse.Gone.newBuilder().setEarliestRevision(1).setReason(GoneReason.GONE_REASON_SLOW)
        state.apply(WatchResponse.newBuilder().setPositionGone(gone).build()).assert().isFalse()
    }

    @Test
    fun `starting over forgets the position and keeps the view`() {
        state.apply(snapshot("a"))
        state.apply(put(2, "half", more = true))
        state.startOver(1)
        state.hasPosition.assert().isFalse()
        state.revision.assert().isEqualTo(0)
        names().assert().containsExactly("a")
        state.apply(put(1, "ignored", more = false, id = 1))
        state.apply(snapshot("b", revision = 3, id = 1))
        names().assert().containsExactly("b")
    }

    @Test
    fun `a fresh snapshot that disagrees at the same position counts as drift`() {
        state.apply(snapshot("a", revision = 5))
        state.startOver(1)
        state.apply(snapshot("a", revision = 5, id = 1))
        state.drift.get().assert().isEqualTo(0)
        state.startOver(2)
        state.apply(snapshot("missed", revision = 5, id = 2))
        state.drift.get().assert().isEqualTo(1)
        names().assert().containsExactly("missed")
    }

    @Test
    fun `a fresh snapshot at another position is not drift`() {
        state.apply(snapshot("a", revision = 5))
        state.startOver(1)
        state.apply(snapshot("b", revision = 6, id = 1))
        state.drift.get().assert().isEqualTo(0)
    }

    private companion object {
        const val OBJECTS = 256
        const val REVISIONS = 1_000L
    }
}
