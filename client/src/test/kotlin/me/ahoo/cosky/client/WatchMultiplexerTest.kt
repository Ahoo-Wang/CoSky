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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.ErrorReason
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.store.Mutation
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import me.ahoo.cosky.server.CoSkyProperties as ServerProperties

class WatchMultiplexerTest {
    // Room for the watches of the test and no more. No progress either: it would wait for the views a test holds.
    private val server = TestServer(
        ServerProperties(
            watch = ServerProperties.Watch(maxPerStream = OLD + NEW, progressInterval = Duration.ofHours(1)),
        ),
    )
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)
    private val options = CoSkyClientOptions(listOf("only")) {
        snapshotDir = null
        reconnectMin = 10.milliseconds
        reconnectMax = 100.milliseconds
        channelFactory = { InProcessChannelBuilder.forName(server.name).build() }
    }
    private val connection = Connection(options)
    private val multiplexer = WatchMultiplexer(connection, options, SnapshotStore(options, scope), scope)

    @BeforeEach
    fun start() {
        server.start()
        runBlocking { server.store.createNamespace(NAMESPACE) }
    }

    @AfterEach
    fun stop() {
        multiplexer.close()
        scope.cancel()
        connection.close()
        server.close()
    }

    private fun watch(config: String): WatchState = multiplexer.watch(WatchKey(NAMESPACE, Kind.KIND_CONFIG, config))

    private fun write(config: String, content: String): Unit = runBlocking {
        val resource = resource {
            meta = objectMeta {
                namespace = NAMESPACE
                name = config
            }
            this.config = configSpec {
                format = ConfigFormat.CONFIG_FORMAT_YAML
                this.content = content
            }
        }
        server.store.apply(NAMESPACE, listOf(Mutation.Put(resource)), "test")
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

    /**
     * Only read, to tell when a new stream has taken over: nothing else shows that before its call starts.
     */
    private fun requests(): Any? =
        WatchMultiplexer::class.java.getDeclaredField("requests").apply { isAccessible = true }.get(multiplexer)

    private fun <T> holding(states: List<WatchState>, block: () -> T): T =
        if (states.isEmpty()) block() else synchronized(states.first()) { holding(states.drop(1), block) }

    @Test
    fun `a watch added while a new stream takes over is created on it once, and leaves room for the others`() {
        val old = List(OLD) { "old-$it" }
        val new = List(NEW) { "new-$it" }
        runBlocking { old.forEach { withTimeout(10.seconds) { watch(it).awaitKnown() } } }
        val before = requests()

        // A new stream goes through every watch, and waits at the first one it comes to while they are held.
        val adding = holding(old.map { watch(it) }) {
            server.stop()
            server.start()
            awaitUntil { requests().let { it != null && it !== before } }
            // Several, since a stream going through the watches may or may not come across one added meanwhile.
            thread { new.forEach { watch(it) } }.also { adding ->
                // Done, or waiting for the stream to finish going through the watches.
                awaitUntil { adding.state == Thread.State.TERMINATED || adding.state == Thread.State.BLOCKED }
            }
        }
        adding.join()

        // Once the server has refused this one, it has gone through every Create before it.
        val refused = runCatching { runBlocking { withTimeout(10.seconds) { watch(EXTRA).awaitKnown() } } }
        (refused.exceptionOrNull() as CoSkyException).reason
            .assert().isEqualTo(ErrorReason.ERROR_REASON_WATCH_QUOTA_EXCEEDED)
        server.watchStreams.last().map { it.name }.assert().containsExactlyInAnyOrderElementsOf(old + new + EXTRA)

        (old + new).forEach { write(it, "fresh") }
        awaitUntil { (old + new).all { watch(it).view.value[it]?.config?.content == "fresh" } }
    }

    @Test
    fun `starting a watch over for a stream that was replaced leaves its route on the new one`() {
        val watch = watch("order.yaml")
        runBlocking { withTimeout(10.seconds) { watch.awaitKnown() } }
        val replaced = requests()
        server.stop()
        server.start()
        awaitUntil { requests().let { it != null && it !== replaced } }
        awaitUntil { routes().containsValue(watch) }
        val id = routes().entries.single { it.value === watch }.key

        // What a resync that read the old queue would do with the new stream's route.
        WatchMultiplexer::class.java
            .getDeclaredMethod("restart", Channel::class.java, Long::class.java, WatchState::class.java)
            .apply { isAccessible = true }
            .invoke(multiplexer, replaced, id, watch)

        routes()[id].assert().isSameAs(watch)
        write("order.yaml", "fresh")
        awaitUntil { watch.view.value["order.yaml"]?.config?.content == "fresh" }
    }

    @Suppress("UNCHECKED_CAST")
    private fun routes(): Map<Long, WatchState> =
        WatchMultiplexer::class.java.getDeclaredField("byId").apply { isAccessible = true }.get(multiplexer)
            as Map<Long, WatchState>

    private companion object {
        const val NAMESPACE = "prod"
        const val OLD = 6
        const val NEW = 5
        const val EXTRA = "one-too-many"
    }
}
