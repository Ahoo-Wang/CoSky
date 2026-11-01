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

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.store.ChangeBatch
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.Position
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class NamespaceCacheTest {
    /**
     * A read that fails on demand, which is what a failover looks like from a follower.
     */
    private class FlakyStore(private val delegate: Store) : Store by delegate {
        val failing = MutableStateFlow(false)

        override fun changes(namespace: String, from: Position): Flow<ChangeBatch> = merge(
            delegate.changes(namespace, from),
            flow {
                failing.first { it }
                failing.value = false
                error("The read failed.")
            },
        )
    }

    @Test
    fun `a store that went back while a read failed counts as a lost timeline`() = runBlocking<Unit> {
        val memory = MemoryStore(keepRevisions = 16)
        val store = FlakyStore(memory)
        memory.createNamespace(NAMESPACE)
        memory.apply(NAMESPACE, listOf(Mutation.Put(config("1"))), "test")
        memory.apply(NAMESPACE, listOf(Mutation.Put(config("2"))), "test")
        val cache = NamespaceCache(NAMESPACE, store, WINDOW)
        withTimeout(10.seconds) {
            val following = launch { cache.run() }
            val before = cache.views.first { it != null && it.revision == 2L }!!

            store.failing.value = true
            delay(100.milliseconds)
            memory.loseWritesAfter(NAMESPACE, 1)

            // Under the same epoch a watcher at revision 2 would skip what the store writes as revision 2 next.
            val after = cache.views.first { it != null && it.revision == 1L }!!
            after.epoch.assert().isNotEqualTo(before.epoch)
            following.cancel()
        }
    }

    private fun config(value: String) = resource {
        meta = objectMeta {
            namespace = NAMESPACE
            name = "a"
        }
        config = configSpec {
            format = ConfigFormat.CONFIG_FORMAT_TEXT
            content = value
        }
    }

    private companion object {
        const val NAMESPACE = "flaky"
        const val WINDOW = 16
    }
}
