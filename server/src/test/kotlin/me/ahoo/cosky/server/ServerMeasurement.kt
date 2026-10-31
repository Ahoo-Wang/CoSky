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

package me.ahoo.cosky.server

import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.WatchRequest
import me.ahoo.cosky.server.watch.NamespaceCache
import me.ahoo.cosky.server.watch.WatchSession
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.key
import me.ahoo.cosky.store.memory.MemoryStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Rough numbers for the targets of design §7.10, reported rather than asserted. Run with `COSKY_MEASURE=true` and a
 * large `COSKY_MEASURE_HEAP`; `COSKY_MEASURE_JVM_ARGS` adds JVM flags such as compact object headers.
 *
 * It measures the fan-out inside one process only: no network, no serialization.
 */
@EnabledIfEnvironmentVariable(named = "COSKY_MEASURE", matches = "true")
class ServerMeasurement {
    private fun instance(index: Int): Resource = RunningServer.instance(NAMESPACE, "order-10.0.0.$index-8080", 0)
        .toBuilder()
        .apply {
            metaBuilder.putLabels("team", "order").putLabels("zone", "az${index % 3}")
            instanceBuilder.setWeight(1).putMetadata("version", "1.2.3").putMetadata("region", "cn-east")
                .putMetadata("instance-id", "i-$index")
        }
        .build()

    private fun usedHeap(): Long {
        repeat(3) {
            System.gc()
            Thread.sleep(100)
        }
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    @Test
    fun `memory per instance`() {
        val before = usedHeap()
        val objects = HashMap<ResourceKey, Resource>(INSTANCES)
        repeat(INSTANCES) {
            val resource = instance(it)
            objects[resource.key] = resource
        }
        val persistent = objects.toPersistentHashMap()
        objects.clear()
        val after = usedHeap()
        println("MEASURE instances=${persistent.size} bytesPerInstance=${(after - before) / INSTANCES} ${jvm()}")
    }

    @Test
    fun `fan-out on virtual threads`() = fanOut("virtual", VirtualThreads.dispatcher)

    @Test
    fun `fan-out on the default dispatcher`() = fanOut("default", Dispatchers.Default)

    private fun fanOut(name: String, dispatcher: CoroutineDispatcher) = runBlocking<Unit>(dispatcher) {
        withTimeout(10.minutes) {
            val store = MemoryStore()
            store.createNamespace(NAMESPACE)
            val cache = NamespaceCache(NAMESPACE, store, WINDOW)
            val following = launch { cache.run() }
            val before = usedHeap()
            val delivered = AtomicInteger()
            val create = WatchRequest.Create.newBuilder().setNamespace(NAMESPACE).setKind(Kind.KIND_INSTANCE).build()
            val started = System.nanoTime()
            val sessions = List(WATCHES) { index ->
                launch {
                    val request = create.toBuilder().setWatchId(index.toLong()).build()
                    WatchSession(request, cache, MAX_MESSAGE_BYTES) { response ->
                        if (response.hasEvents() || response.hasSnapshot()) {
                            delivered.incrementAndGet()
                        }
                    }.run()
                }
            }
            while (delivered.get() < WATCHES) {
                delay(10.milliseconds)
            }
            val startMillis = (System.nanoTime() - started) / NANOS_PER_MILLI
            val perWatch = (usedHeap() - before) / WATCHES
            val samples = List(EVENTS) { index ->
                delivered.set(0)
                val publish = System.nanoTime()
                store.apply(NAMESPACE, listOf(Mutation.Put(instance(index))), "measure")
                while (delivered.get() < WATCHES) {
                    delay(1.milliseconds)
                }
                (System.nanoTime() - publish) / NANOS_PER_MILLI
            }.sorted()
            println(
                "MEASURE $name watches=$WATCHES startMs=$startMillis bytesPerWatch=$perWatch " +
                    "eventMs(p50=${samples[samples.size / 2]} p99=${samples[samples.size * 99 / 100]} " +
                    "max=${samples.last()}) ${jvm()}",
            )
            sessions.forEach { it.cancel() }
            following.cancel()
        }
    }

    private fun jvm(): String =
        "jdk=${Runtime.version().feature()} cpus=${Runtime.getRuntime().availableProcessors()} " +
            "args=${System.getenv("COSKY_MEASURE_JVM_ARGS").orEmpty()}"

    private companion object {
        const val NAMESPACE = "measure"
        const val INSTANCES = 50_000
        const val WATCHES = 100_000
        const val EVENTS = 50
        const val WINDOW = 1000
        const val MAX_MESSAGE_BYTES = 2 * 1024 * 1024
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
