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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.store.Mutation
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A snapshot on disk lets a client start without a server, but where a server answers, what it says comes first: a
 * snapshot that is out of date, or from another server at the same address, would otherwise be read as the truth.
 */
class SnapshotFreshnessTest {
    private val server = TestServer()
    private val clients = mutableListOf<CoSkyClient>()

    @TempDir
    lateinit var snapshots: Path

    @BeforeEach
    fun start() {
        server.start()
        runBlocking { server.store.createNamespace(NAMESPACE) }
    }

    @AfterEach
    fun stop() {
        clients.forEach { it.close() }
        server.close()
    }

    @Test
    fun `a load where a server answers reads what it says rather than an older snapshot`() = scenario {
        saveSnapshotOf("saved")
        write(config("newer"))

        client().namespace(NAMESPACE).configs.load(CONFIG)?.config?.content.assert().isEqualTo("newer")
    }

    @Test
    fun `a load where no server answers reads the snapshot`() = scenario {
        saveSnapshotOf("saved")
        server.stop()

        client().namespace(NAMESPACE).configs.load(CONFIG)?.config?.content.assert().isEqualTo("saved")
    }

    @Test
    fun `a load whose snapshot is current reads it without waiting out the moment`() = scenario {
        saveSnapshotOf("saved")

        val started = System.nanoTime()
        client().namespace(NAMESPACE).configs.load(CONFIG)?.config?.content.assert().isEqualTo("saved")
        // The server says at once that the snapshot is current, rather than at its next bookmark.
        Duration.parse("${System.nanoTime() - started}ns").assert().isLessThan(SERVER_WAIT)
    }

    /**
     * Leaves a snapshot of the Config with [content] on disk, as a client that ran before did.
     */
    private suspend fun saveSnapshotOf(content: String) {
        write(config(content))
        val first = client()
        first.namespace(NAMESPACE).configs.watch(CONFIG).first { it?.config?.content == content }
        while (snapshotFiles().isEmpty()) {
            delay(10.milliseconds)
        }
        delay(50.milliseconds)
        first.close()
        clients.remove(first)
    }

    private fun snapshotFiles(): List<Path> = runCatching {
        Files.walk(snapshots).use { paths -> paths.filter { it.isRegularFile() && it.name.endsWith(".pb") }.toList() }
    }.getOrDefault(emptyList())

    private fun client(): CoSkyClient = CoSkyClient(
        CoSkyClientOptions(listOf("only")) {
            snapshotDir = snapshots
            snapshotDebounce = 10.milliseconds
            reconnectMin = 10.milliseconds
            reconnectMax = 100.milliseconds
            channelFactory = { InProcessChannelBuilder.forName(server.name).build() }
        },
    ).also { clients += it }

    private suspend fun write(resource: Resource) {
        server.store.apply(NAMESPACE, listOf(Mutation.Put(resource)), "test")
    }

    private fun config(content: String): Resource = resource {
        meta = objectMeta {
            namespace = NAMESPACE
            name = CONFIG
        }
        config = configSpec {
            format = ConfigFormat.CONFIG_FORMAT_YAML
            this.content = content
        }
    }

    private fun scenario(block: suspend CoroutineScope.() -> Unit): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30.seconds, block)
    }

    private companion object {
        const val NAMESPACE = "prod"
        const val CONFIG = "application.yaml"

        /** What a view known only from a snapshot waits for the server at most, in [WatchState]. */
        val SERVER_WAIT = 1.seconds
    }
}
