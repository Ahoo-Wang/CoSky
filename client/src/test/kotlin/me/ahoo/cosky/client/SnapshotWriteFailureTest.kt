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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.ListResponse
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration

/**
 * A directory the client cannot write to fails every snapshot. Set on purpose, that is warned about once, with what to
 * do, rather than at every write, and its return said once. The default, under a home that a container user often does
 * not have, turns snapshots off instead, said once at info level.
 */
class SnapshotWriteFailureTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val logs = ListAppender<ILoggingEvent>().apply { start() }
    private val logger = (LoggerFactory.getLogger(SnapshotStore::class.java) as Logger).also { it.addAppender(logs) }

    @TempDir
    lateinit var directory: Path

    /** A file where the snapshots' directory would go, so that making it fails as an unwritable home does. */
    private val blocker by lazy { directory.resolve("home") }

    private val store by lazy {
        SnapshotStore(
            CoSkyClientOptions(listOf("only")) {
                snapshotDir = blocker.resolve(".cosky")
                snapshotDebounce = Duration.ZERO
            },
            scope,
        )
    }

    @AfterEach
    fun stop() {
        logger.detachAppender(logs)
        scope.cancel()
    }

    @Test
    fun `snapshots that cannot be written are said once, with what to do, and their return once`() = runBlocking<Unit> {
        Files.writeString(blocker, "not a directory")
        repeat(WRITES) { save(instances("service-$it")) }

        val warnings = logs.list.filter { it.level == Level.WARN }
        warnings.size.assert().isEqualTo(1)
        warnings.single().formattedMessage.assert().contains("cosky.snapshot.dir").contains("cosky.snapshot.enabled")

        Files.delete(blocker)
        save(instances("service-0"))
        save(instances("service-1"))

        store.load(instances("service-0")).assert().isNotNull()
        logs.list.filter { it.level == Level.INFO }.map { it.formattedMessage }.assert()
            .singleElement().asString().contains("again")
        logs.list.count { it.level == Level.WARN }.assert().isEqualTo(1)
    }

    @Test
    fun `the default directory under a home that is not there turns snapshots off, said once at info level`() =
        runBlocking<Unit> {
            val default = directory.resolve("missing-home").resolve(".cosky")
            val store = storeOf(default, default)
            repeat(WRITES) { save(store, instances("service-$it")) }

            Files.exists(default).assert().isFalse()
            logs.list.count { it.level == Level.WARN }.assert().isEqualTo(0)
            logs.list.filter { it.level == Level.INFO }.map { it.formattedMessage }.assert()
                .singleElement().asString().contains("Snapshots are off").contains("cosky.snapshot.dir")
        }

    @Test
    fun `the default directory under a home that may be written keeps snapshots`() = runBlocking<Unit> {
        val default = directory.resolve("home").resolve(".cosky")
        Files.createDirectory(default.parent)
        val store = storeOf(default, default)
        save(store, instances("service-0"))

        store.load(instances("service-0")).assert().isNotNull()
        logs.list.assert().isEmpty()
    }

    private fun storeOf(snapshots: Path, default: Path) = SnapshotStore(
        CoSkyClientOptions(listOf("only")) {
            snapshotDir = snapshots
            snapshotDebounce = Duration.ZERO
        },
        scope,
        default,
    )

    private fun instances(service: String) = WatchKey("prod", Kind.KIND_INSTANCE, service = service)

    private suspend fun save(key: WatchKey) = save(store, key)

    private suspend fun save(store: SnapshotStore, key: WatchKey) {
        store.schedule(key) { viewOf(key) }
        scope.coroutineContext.job.children.toList().joinAll()
    }

    private fun viewOf(key: WatchKey): ListResponse = ListResponse.newBuilder()
        .addItems(resource { meta = objectMeta { name = key.service } })
        .setEpoch("e")
        .setRevision(1)
        .build()

    private companion object {
        const val WRITES = 5
    }
}
