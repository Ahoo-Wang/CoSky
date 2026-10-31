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

package me.ahoo.cosky.server.view

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.api.v1.TopologyEdge
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class TopologyRecorderTest {
    private val store = MemoryStore()

    private fun edge(consumer: String, producer: String): TopologyEdge =
        TopologyEdge.newBuilder().setConsumer(consumer).setProducer(producer).build()

    @Test
    fun `two replicas see each other's watchers`() = runBlocking<Unit> {
        store.createNamespace(NAMESPACE)
        val first = TopologyRecorder(store, 1.minutes)
        val second = TopologyRecorder(store, 1.minutes)
        first.watching(NAMESPACE, "order", "payment")
        second.watching(NAMESPACE, "cart", "payment")
        first.flush()
        second.flush()
        store.topology(NAMESPACE).assert().containsExactlyInAnyOrder(edge("order", "payment"), edge("cart", "payment"))
    }

    @Test
    fun `an edge stays while any of its watches lasts`() = runBlocking<Unit> {
        store.createNamespace(NAMESPACE)
        val recorder = TopologyRecorder(store, SHORT)
        val one = recorder.watching(NAMESPACE, "order", "payment")
        val other = recorder.watching(NAMESPACE, "order", "payment")
        one.close()
        recorder.flush()
        delay(SHORT * 2)
        recorder.flush()
        store.topology(NAMESPACE).assert().containsExactly(edge("order", "payment"))

        other.close()
        recorder.flush()
        delay(SHORT * 4)
        store.topology(NAMESPACE).assert().isEmpty()
    }

    private companion object {
        const val NAMESPACE = "topology"
        val SHORT = 100.milliseconds
    }
}
