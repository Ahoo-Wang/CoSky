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

import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.WatchRequest
import me.ahoo.cosky.api.v1.WatchResponse
import me.ahoo.cosky.api.v1.WatchServiceGrpcKt
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.lang.management.ManagementFactory
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds

/**
 * Responses still on their way to the id a watch had before it started over reach none of its views (design §7.5).
 * The server here only takes requests, so that the test hands the multiplexer every response itself, in the order the
 * race needs.
 */
class StaleWatchResponseTest {
    private val name = "cosky-${UUID.randomUUID()}"
    private val server = InProcessServerBuilder.forName(name)
        .addService(
            object : WatchServiceGrpcKt.WatchServiceCoroutineImplBase() {
                override fun watch(requests: Flow<WatchRequest>): Flow<WatchResponse> = flow { requests.collect {} }
            },
        )
        .build()
        .start()
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)
    private val options = CoSkyClientOptions(listOf("only")) {
        snapshotDir = null
        channelFactory = { InProcessChannelBuilder.forName(name).build() }
    }
    private val connection = Connection(options)
    private val multiplexer = WatchMultiplexer(connection, options, SnapshotStore(options, scope), scope)

    @AfterEach
    fun stop() {
        multiplexer.close()
        scope.cancel()
        connection.close()
        server.shutdownNow()
    }

    @Test
    fun `the rest of a snapshot to the id a watch had before a resync does not reach its new snapshot`() {
        val watch = multiplexer.watch(WatchKey("prod", Kind.KIND_CONFIG))
        awaitUntil { requests() != null && routes().containsValue(watch) }
        val outgoing = checkNotNull(requests())
        val old = routes().entries.single { it.value === watch }.key
        handle(outgoing, snapshot(old, "deleted-since", more = true))

        // The stream's coroutine has found the watch by its old id, and waits for it while a resync starts it over.
        val ghost = snapshot(old, "ghost", more = true)
        val stale = synchronized(watch) {
            thread { handle(outgoing, ghost) }.also { handling ->
                awaitUntil { waitsFor(handling, watch) }
                restart(outgoing, old, watch)
            }
        }
        stale.join()

        val new = routes().entries.single { it.value === watch }.key
        new.assert().isNotEqualTo(old)
        handle(outgoing, snapshot(new, "fresh"))
        watch.view.value.keys.assert().containsExactly("fresh")
    }

    private fun snapshot(id: Long, item: String, more: Boolean = false): WatchResponse = WatchResponse.newBuilder()
        .setWatchId(id)
        .setSnapshot(
            WatchResponse.Snapshot.newBuilder()
                .addItems(resource { meta = objectMeta { name = item } })
                .setEpoch("e")
                .setRevision(1)
                .setMore(more),
        )
        .build()

    /** Whether [thread] is blocked on entering the monitor of [monitor]. */
    private fun waitsFor(thread: Thread, monitor: Any): Boolean {
        val info = ManagementFactory.getThreadMXBean().getThreadInfo(thread.threadId()) ?: return false
        return info.threadState == Thread.State.BLOCKED &&
            info.lockInfo?.identityHashCode == System.identityHashCode(monitor)
    }

    /**
     * Polls rather than suspends, since a thread that holds a monitor cannot suspend.
     */
    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10.seconds.inWholeNanoseconds
        while (!condition()) {
            check(System.nanoTime() < deadline) { "Gave up waiting." }
            Thread.sleep(10)
        }
    }

    private fun requests(): Channel<*>? =
        WatchMultiplexer::class.java.getDeclaredField("requests").apply { isAccessible = true }.get(multiplexer)
            as Channel<*>?

    @Suppress("UNCHECKED_CAST")
    private fun routes(): Map<Long, WatchState> =
        WatchMultiplexer::class.java.getDeclaredField("byId").apply { isAccessible = true }.get(multiplexer)
            as Map<Long, WatchState>

    /** What the stream's coroutine does with a response. */
    private fun handle(outgoing: Channel<*>, response: WatchResponse) {
        WatchMultiplexer::class.java
            .getDeclaredMethod("handle", Channel::class.java, WatchResponse::class.java)
            .apply { isAccessible = true }
            .invoke(multiplexer, outgoing, response)
    }

    /** What a resync does with each route. */
    private fun restart(outgoing: Channel<*>, id: Long, watch: WatchState) {
        WatchMultiplexer::class.java
            .getDeclaredMethod("restart", Channel::class.java, Long::class.java, WatchState::class.java)
            .apply { isAccessible = true }
            .invoke(multiplexer, outgoing, id, watch)
    }
}
