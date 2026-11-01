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

import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.ListResponse
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.WatchResponse
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class WatchStateTest {
    private val state = WatchState(WatchKey("prod", Kind.KIND_CONFIG), null)

    private fun config(name: String): Resource = resource { meta = objectMeta { this.name = name } }

    private fun snapshot(vararg names: String, revision: Long = 1, epoch: String = "e", more: Boolean = false) =
        WatchResponse.newBuilder().setSnapshot(
            WatchResponse.Snapshot.newBuilder()
                .addAllItems(names.map { config(it) })
                .setRevision(revision)
                .setEpoch(epoch)
                .setMore(more),
        ).build()

    private fun events(revision: Long, type: WatchResponse.Event.Type, name: String, more: Boolean = false) =
        WatchResponse.newBuilder().setEvents(
            WatchResponse.Events.newBuilder()
                .setRevision(revision)
                .setMore(more)
                .addEvents(WatchResponse.Event.newBuilder().setType(type).setResource(config(name))),
        ).build()

    private fun put(revision: Long, name: String, more: Boolean = false) =
        events(revision, WatchResponse.Event.Type.TYPE_PUT, name, more)

    private fun names(): Set<String> = state.view.value.keys

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
        state.discardPartial()
        state.apply(snapshot("kept")).assert().isTrue()
        names().assert().containsExactly("kept")
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
    fun `a bookmark only moves forward`() {
        state.apply(snapshot("a", revision = 5))
        state.apply(WatchResponse.newBuilder().setProgress(9).build()).assert().isTrue()
        state.revision.assert().isEqualTo(9)
        state.apply(WatchResponse.newBuilder().setProgress(3).build()).assert().isFalse()
        state.revision.assert().isEqualTo(9)
    }

    @Test
    fun `other responses change nothing`() {
        state.apply(WatchResponse.newBuilder().setGone(1).build()).assert().isFalse()
    }

    @Test
    fun `starting over forgets the position and keeps the view`() {
        state.apply(snapshot("a"))
        state.apply(put(2, "half", more = true))
        state.startOver()
        state.hasPosition.assert().isFalse()
        state.revision.assert().isEqualTo(0)
        names().assert().containsExactly("a")
        state.apply(put(1, "ignored", more = false))
        state.apply(snapshot("b", revision = 3))
        names().assert().containsExactly("b")
    }

    @Test
    fun `a fresh snapshot that disagrees at the same position counts as drift`() {
        state.apply(snapshot("a", revision = 5))
        state.startOver()
        state.apply(snapshot("a", revision = 5))
        state.drift.get().assert().isEqualTo(0)
        state.startOver()
        state.apply(snapshot("missed", revision = 5))
        state.drift.get().assert().isEqualTo(1)
        names().assert().containsExactly("missed")
    }

    @Test
    fun `a fresh snapshot at another position is not drift`() {
        state.apply(snapshot("a", revision = 5))
        state.startOver()
        state.apply(snapshot("b", revision = 6))
        state.drift.get().assert().isEqualTo(0)
    }
}
