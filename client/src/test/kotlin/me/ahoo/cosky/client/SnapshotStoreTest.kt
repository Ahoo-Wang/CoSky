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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.ListResponse
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class SnapshotStoreTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @TempDir
    lateinit var directory: Path

    private val store by lazy {
        SnapshotStore(
            CoSkyClientOptions(endpoints = listOf("only"), snapshotDir = directory, snapshotDebounce = Duration.ZERO),
            scope,
        )
    }

    @AfterEach
    fun stop() {
        scope.cancel()
    }

    private fun config(name: String) = WatchKey("prod", Kind.KIND_CONFIG, name = name)

    private fun instances(service: String, namespace: String = "prod") =
        WatchKey(namespace, Kind.KIND_INSTANCE, service = service)

    /**
     * A view that names its watch, to tell whose snapshot comes back.
     */
    private fun viewOf(key: WatchKey, revision: Long = 1): ListResponse = ListResponse.newBuilder()
        .addItems(resource { meta = objectMeta { name = key.name.ifEmpty { key.service } } })
        .setEpoch("e")
        .setRevision(revision)
        .build()

    /**
     * Waits until the writes are over, whether or not they worked: a write that fails only logs.
     */
    private fun save(keys: List<WatchKey>) = runBlocking {
        keys.forEach { key -> store.schedule(key) { viewOf(key) } }
        awaitJobs()
    }

    private fun loaded(key: WatchKey): String? = store.load(key)?.itemsList?.single()?.meta?.name

    private suspend fun awaitJobs() = scope.coroutineContext.job.children.toList().joinAll()

    @Test
    fun `snapshots written at once all land, though the directory they share is not there yet`() {
        // Each namespace is a directory of its own, so each round starts without one.
        val keys = (1..ROUNDS).flatMap { round -> List(WATCHES) { instances("service-$it", "prod-$round") } }
        save(keys)
        keys.map { loaded(it) }.assert().isEqualTo(keys.map { it.service })
    }

    @Test
    fun `every config name the protocol allows gets a snapshot of its own`() {
        val names = listOf(
            "a".repeat(NamingRules.MAX_CONFIG_NAME_LENGTH),
            "a/".repeat(NamingRules.MAX_CONFIG_NAME_LENGTH / 2) + "a",
            "order.yaml",
            "Order.yaml",
        )
        names.assert().allMatch { NamingRules.isConfigName(it) }
        save(names.map { config(it) })
        names.map { loaded(config(it)) }.assert().isEqualTo(names)
    }

    @Test
    fun `an earlier write still under way does not replace a later one`() = runBlocking<Unit> {
        val key = instances("order")
        val reading = CountDownLatch(1)
        val resume = CountDownLatch(1)
        store.schedule(key) {
            reading.countDown()
            resume.await()
            viewOf(key, revision = 1)
        }
        reading.await()
        store.schedule(key) { viewOf(key, revision = 2) }
        // Gives the later write every chance to land first, which is how the earlier one came to replace it.
        withTimeoutOrNull(LATER_WRITE_TIME) {
            while (store.load(key)?.revision != 2L) {
                delay(10.milliseconds)
            }
        }
        resume.countDown()
        awaitJobs()
        store.load(key)?.revision.assert().isEqualTo(2L)
    }

    private companion object {
        val LATER_WRITE_TIME = 500.milliseconds
        const val ROUNDS = 8
        const val WATCHES = 32
    }
}
