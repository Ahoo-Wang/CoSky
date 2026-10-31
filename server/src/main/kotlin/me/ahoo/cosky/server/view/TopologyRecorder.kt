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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import me.ahoo.cosky.api.v1.TopologyEdge
import me.ahoo.cosky.store.Store
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

/**
 * What this replica's watchers consume (design D30). It writes the edges to the store periodically, since each replica
 * only sees its own watchers, and an edge expires unless some replica keeps refreshing it.
 */
class TopologyRecorder(private val store: Store, private val flushInterval: Duration) {
    private val live = ConcurrentHashMap<String, ConcurrentHashMap<TopologyEdge, Int>>()

    /**
     * @return closes when the watch ends.
     */
    fun watching(namespace: String, consumer: String, producer: String): AutoCloseable {
        val edges = live.computeIfAbsent(namespace) { ConcurrentHashMap() }
        val edge = TopologyEdge.newBuilder().setConsumer(consumer).setProducer(producer).build()
        edges.merge(edge, 1, Int::plus)
        return AutoCloseable { edges.computeIfPresent(edge) { _, count -> (count - 1).takeIf { it > 0 } } }
    }

    fun hasEdges(namespace: String): Boolean = live[namespace].orEmpty().isNotEmpty()

    suspend fun run() {
        while (true) {
            delay(flushInterval)
            try {
                flush()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                log.warn("Recording the topology failed; the next round tries again.", e)
            }
        }
    }

    suspend fun flush() {
        live.forEach { (namespace, edges) ->
            if (edges.isNotEmpty()) {
                store.recordTopology(namespace, edges.keys.toList(), flushInterval * RETAINED_INTERVALS)
            }
        }
    }

    private companion object {
        const val RETAINED_INTERVALS = 3
        val log = LoggerFactory.getLogger(TopologyRecorder::class.java)
    }
}
