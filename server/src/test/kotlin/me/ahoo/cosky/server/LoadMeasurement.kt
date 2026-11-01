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

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.Endpoint
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.PutRequest
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceServiceGrpcKt
import me.ahoo.cosky.api.v1.WatchRequest
import me.ahoo.cosky.api.v1.WatchResponse
import me.ahoo.cosky.api.v1.WatchServiceGrpc
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.instanceSpec
import me.ahoo.cosky.api.v1.namespaceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.client.CoSkyClient
import me.ahoo.cosky.client.CoSkyClientOptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.File
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The targets of design §7.10 over real gRPC, with the server in a JVM of its own on real Redis. It reports rather than
 * asserts: run it with `COSKY_MEASURE=load`, a large `COSKY_MEASURE_HEAP`, and `COSKY_LOAD_CONNECTIONS` to taste.
 */
@EnabledIfEnvironmentVariable(named = "COSKY_MEASURE", matches = "load")
class LoadMeasurement {
    private val connections = env("COSKY_LOAD_CONNECTIONS", 5_000)
    private val watchesPerConnection = env("COSKY_LOAD_WATCHES_PER_CONNECTION", 20)
    private val instances = env("COSKY_LOAD_INSTANCES", 50_000)
    private val configs = env("COSKY_LOAD_CONFIGS", 10_000)
    private val propagationWrites = env("COSKY_LOAD_PROPAGATION_WRITES", PROPAGATION_WRITES)
    private val propagationPause = env("COSKY_LOAD_PROPAGATION_PAUSE_MS", DEFAULT_PROPAGATION_PAUSE_MS).milliseconds
    private val http = HttpClient.newHttpClient()

    private fun env(name: String, default: Int): Int = System.getenv(name)?.toInt() ?: default

    private fun report(line: String) = println("MEASURE $line")

    /**
     * A Redis of its own, or `COSKY_LOAD_REDIS_URI`: inside a container, the ports of another one may not be reachable.
     */
    private suspend fun withRedis(block: suspend (uri: String) -> Unit) {
        val given = System.getenv("COSKY_LOAD_REDIS_URI")
        if (given != null) {
            block(given)
            return
        }
        RedisContainer().use { redis ->
            redis.start()
            block("redis://${redis.host}:${redis.getMappedPort(RedisContainer.REDIS_PORT)}")
        }
    }

    private inner class ServerProcess(
        redisUri: String,
        private val heap: String = System.getenv("COSKY_LOAD_SERVER_HEAP") ?: "6g",
    ) : AutoCloseable {
        val httpPort = ServerSocket(0).use { it.localPort }
        val grpcPort = ServerSocket(0).use { it.localPort }
        private val process: Process = ProcessBuilder(
            File(System.getProperty("java.home"), "bin/java").path,
            "-Xmx$heap",
            "-XX:+UseCompactObjectHeaders",
            "-cp",
            System.getProperty("java.class.path"),
            "me.ahoo.cosky.server.CoSkyServerApplicationKt",
            "--server.port=$httpPort",
            "--spring.grpc.server.port=$grpcPort",
            "--cosky.auth.enabled=false",
            "--cosky.store.redis.uri=$redisUri",
            "--logging.level.root=WARN",
            "--cosky.watch.window=${env("COSKY_LOAD_WINDOW", DEFAULT_WINDOW)}",
        )
            .redirectErrorStream(true)
            .redirectOutput(File.createTempFile("cosky-load", ".log").also { report("server log $it") })
            .start()

        suspend fun awaitReady(): Long {
            val started = System.nanoTime()
            while (!ready()) {
                delay(50.milliseconds)
            }
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        }

        private fun ready(): Boolean =
            runCatching { get("/actuator/health/readiness") }.getOrNull()?.contains("UP") == true

        fun get(path: String): String = http.send(
            HttpRequest.newBuilder(URI("http://localhost:$httpPort$path")).build(),
            BodyHandlers.ofString(),
        ).body()

        /**
         * What the heap holds after a collection, on the same process's own word.
         */
        fun liveHeapMb(): Long {
            val jcmd = File(System.getProperty("java.home"), "bin/jcmd").path
            ProcessBuilder(jcmd, process.pid().toString(), "GC.run").start().waitFor()
            val used = get("/actuator/prometheus").lineSequence()
                .filter { it.startsWith("jvm_memory_used_bytes") && "area=\"heap\"" in it }
                .sumOf { it.substringAfterLast(' ').toDouble() }
            return (used / MB).toLong()
        }

        fun metric(name: String): Double = get("/actuator/prometheus").lineSequence()
            .filter { it.startsWith(name) }
            .sumOf { it.substringAfterLast(' ').toDouble() }

        override fun close() {
            process.destroy()
            if (!process.waitFor(STOP_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
        }
    }

    private fun channel(port: Int): ManagedChannel =
        ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build()

    private fun instance(name: String, service: String, sentAt: Long = 0): Resource = resource {
        meta = objectMeta {
            namespace = NAMESPACE
            this.name = name
            labels.put("team", "order")
            labels.put("zone", "az1")
        }
        instance = instanceSpec {
            this.service = service
            weight = 1
            endpoint = ENDPOINT
            metadata.put("version", "1.2.3")
            metadata.put("region", "cn-east")
            metadata.put("sent-at", sentAt.toString())
        }
    }

    private fun config(name: String): Resource = resource {
        meta = objectMeta {
            namespace = NAMESPACE
            this.name = name
        }
        config = configSpec {
            format = ConfigFormat.CONFIG_FORMAT_YAML
            content = "datasource:\n  url: jdbc:mysql://db/$name\n  pool: 16\n"
        }
    }

    private fun percentiles(samples: List<Long>): String {
        val sorted = samples.sorted()
        fun at(share: Double) = sorted[((sorted.size - 1) * share).toInt()]
        return "n=${sorted.size} p50=${at(P50)}ms p95=${at(P95)}ms p99=${at(P99)}ms max=${sorted.last()}ms"
    }

    private suspend fun putAll(resources: ResourceServiceGrpcKt.ResourceServiceCoroutineStub, items: List<Resource>) =
        coroutineScope {
            val permits = Semaphore(PRELOAD_CONCURRENCY)
            items.map { item ->
                async { permits.withPermit { resources.put(PutRequest.newBuilder().setResource(item).build()) } }
            }.awaitAll()
        }

    private suspend fun writeLatency(
        resources: ResourceServiceGrpcKt.ResourceServiceCoroutineStub,
        writers: Int,
        writesEach: Int,
    ) = coroutineScope {
        val samples = ConcurrentLinkedQueue<Long>()
        val started = System.nanoTime()
        List(writers) { writer ->
            launch {
                repeat(writesEach) { index ->
                    val before = System.nanoTime()
                    resources.put(
                        PutRequest.newBuilder().setResource(config("latency-$writer-${index % 16}")).build(),
                    )
                    samples += TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - before)
                }
            }
        }.forEach { it.join() }
        val seconds = (System.nanoTime() - started) / NANOS_PER_SECOND
        val perSecond = (samples.size / seconds).toInt()
        report("write writers=$writers rate=$perSecond/s ${percentiles(samples.map { it / MICROS_PER_MILLI })}")
    }

    /**
     * One stream per connection, and a watch per service on it, as an application watching what it calls.
     */
    private fun openWatches(
        channels: List<ManagedChannel>,
        delivered: ConcurrentLinkedQueue<Long>,
        snapshots: AtomicInteger,
        failures: ConcurrentLinkedQueue<String>,
    ): List<StreamObserver<WatchRequest>> = channels.map { channel ->
        val responses = object : StreamObserver<WatchResponse> {
            override fun onNext(response: WatchResponse) {
                when {
                    response.hasSnapshot() -> snapshots.incrementAndGet()
                    response.hasEvents() -> {
                        val now = System.nanoTime()
                        response.events.eventsList.forEach {
                            val sentAt = it.resource.instance.metadataMap["sent-at"]?.toLongOrNull() ?: 0
                            if (sentAt != 0L) {
                                delivered += TimeUnit.NANOSECONDS.toMillis(now - sentAt)
                            }
                        }
                    }
                }
            }

            override fun onError(failure: Throwable) {
                failures += failure.message.orEmpty().take(FAILURE_LENGTH)
            }

            override fun onCompleted() = Unit
        }
        val requests = WatchServiceGrpc.newStub(channel).watch(responses)
        repeat(watchesPerConnection) { index ->
            requests.onNext(
                WatchRequest.newBuilder().apply {
                    createBuilder
                        .setWatchId(index.toLong())
                        .setNamespace(NAMESPACE)
                        .setKind(Kind.KIND_INSTANCE)
                        .setService("service-$index")
                }.build(),
            )
        }
        requests
    }

    @Suppress("LongMethod") // One measurement after another, in order, is clearer than split up.
    @Test
    fun `the targets of design 7 10`() = runBlocking<Unit>(Dispatchers.Default) {
        withRedis { redis ->
            ServerProcess(redis).use { server ->
                report("server ready in ${server.awaitReady()}ms")
                val writeChannel = channel(server.grpcPort)
                val resources = ResourceServiceGrpcKt.ResourceServiceCoroutineStub(writeChannel).withWaitForReady()
                val namespace = resource {
                    meta = objectMeta {
                        this.namespace = "_system"
                        name = NAMESPACE
                    }
                    this.namespace = namespaceSpec {}
                }
                resources.put(PutRequest.newBuilder().setResource(namespace).build())
                val emptyHeap = server.liveHeapMb()

                // --- scale of one namespace ---
                val loading = System.nanoTime()
                putAll(
                    resources,
                    List(instances) { instance("order-$it", "service-${it % SERVICES}") } +
                        List(configs) { config("config-$it") },
                )
                val loadSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - loading)
                val loadedHeap = server.liveHeapMb()
                report(
                    "loaded instances=$instances configs=$configs in ${loadSeconds}s, " +
                        "heap ${emptyHeap}MB -> ${loadedHeap}MB",
                )

                // --- write latency ---
                writeLatency(resources, 1, WRITES)
                writeLatency(resources, WRITERS, WRITES / WRITERS)

                // --- connections and watches ---
                val opening = System.nanoTime()
                val delivered = ConcurrentLinkedQueue<Long>()
                val snapshots = AtomicInteger()
                val channels = List(connections) { channel(server.grpcPort) }
                val failures = ConcurrentLinkedQueue<String>()
                val streams = openWatches(channels, delivered, snapshots, failures)
                val expected = connections * watchesPerConnection
                while (snapshots.get() + failures.size * watchesPerConnection < expected && isActive) {
                    delay(200.milliseconds)
                    if (TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - opening) > OPEN_TIMEOUT_SECONDS) {
                        report("gave up opening: ${snapshots.get()} of $expected snapshots")
                        break
                    }
                }
                val openSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - opening)
                failures.groupingBy { it }.eachCount().forEach { (failure, count) ->
                    report("failed streams: $count $failure")
                }
                val watchedHeap = server.liveHeapMb()
                report(
                    "connections=$connections watches=$expected snapshots=${snapshots.get()} in ${openSeconds}s, " +
                        "heap ${loadedHeap}MB -> ${watchedHeap}MB",
                )

                // --- propagation: each write reaches every connection, through a watch among many ---
                repeat(propagationWrites) { index ->
                    val written = instance("propagating", "service-0", System.nanoTime())
                        .toBuilder().apply { metaBuilder.putLabels("write", index.toString()) }.build()
                    resources.put(PutRequest.newBuilder().setResource(written).build())
                    delay(propagationPause)
                }
                delay(PROPAGATION_SETTLE)
                val samples = delivered.toList()
                report(
                    "propagation expected=${connections * propagationWrites} pause=$propagationPause " +
                        percentiles(samples.ifEmpty { listOf(-1L) }),
                )
                report(
                    "server gone=${server.metric(
                        "cosky_watch_gone_total"
                    )} active=${server.metric("cosky_watch_active")}"
                )

                streams.forEach { runCatching { it.onCompleted() } }
                channels.forEach { it.shutdownNow() }
                writeChannel.shutdownNow()

                // --- a replica starting on all of this ---
                ServerProcess(redis).use { second ->
                    report("a second replica loaded everything and is ready in ${second.awaitReady()}ms")
                    report("second replica heap ${second.liveHeapMb()}MB")
                }
            }
        }
    }

    /**
     * Design D29: a rolling restart under a steady stream of writes must not make clients List again.
     */
    @Test
    fun `the window through a rolling restart`() = runBlocking<Unit>(Dispatchers.Default) {
        withRedis { redis ->
            val rates = System.getenv("COSKY_LOAD_RATES")?.split(",")?.map { it.toInt() }
                ?: listOf(WINDOW_SLOW_RATE, WINDOW_FAST_RATE)
            rates.forEach { rate -> rollingRestart(redis, rate) }
        }
    }

    @Suppress("LongMethod") // One scenario, in order.
    private suspend fun rollingRestart(redis: String, rate: Int) = coroutineScope {
        val first = ServerProcess(redis, "2g")
        val second = ServerProcess(redis, "2g")
        first.awaitReady()
        second.awaitReady()
        val channel = channel(second.grpcPort)
        val resources = ResourceServiceGrpcKt.ResourceServiceCoroutineStub(channel).withWaitForReady()
        val namespace = resource {
            meta = objectMeta {
                this.namespace = "_system"
                name = NAMESPACE
            }
            this.namespace = namespaceSpec {}
        }
        resources.put(PutRequest.newBuilder().setResource(namespace).build())

        val clientCount = env("COSKY_LOAD_WINDOW_CLIENTS", WINDOW_CLIENTS)
        val clients = List(clientCount) {
            CoSkyClient(
                CoSkyClientOptions(
                    endpoints = listOf("localhost:${first.grpcPort}", "localhost:${second.grpcPort}"),
                    snapshotDir = null,
                ),
            )
        }
        val views = clients.map { it.namespace(NAMESPACE).instances.watch("churn") }

        var written = 0
        val writing = launch {
            while (isActive) {
                val tick = System.nanoTime()
                putAll(
                    resources,
                    List(rate / TICKS_PER_SECOND) { instance("churn-${written++ % CHURN}", "churn", System.nanoTime()) }
                )
                val spent = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tick)
                delay((MILLIS_PER_TICK - spent).coerceAtLeast(0).milliseconds)
            }
        }
        delay(WINDOW_WARM_UP)
        val goneBefore = second.metric("cosky_watch_gone_total")
        report("rolling restart before the kill: revision on the first ${first.metric("cosky_cache_revision")}")
        val killedAt = System.nanoTime()
        first.close()
        val writtenBefore = written
        delay(WINDOW_AFTER_KILL)
        writing.cancel()
        val achieved = (written - writtenBefore) * MILLIS_PER_SECOND / WINDOW_AFTER_KILL.inWholeMilliseconds
        val converged = System.nanoTime()
        views.forEach { view -> view.first { it.size == CHURN.coerceAtMost(written) } }
        val gone = second.metric("cosky_watch_gone_total") - goneBefore
        second.get("/actuator/prometheus").lineSequence()
            .filter { it.startsWith("cosky_watch_gone_total") }
            .forEach { report("rolling restart $it") }
        report(
            "rolling restart target=$rate/s achieved=$achieved/s clients=$clientCount gone=$gone " +
                "converged ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - converged)}ms after writes stopped, " +
                "${TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - killedAt)}s after the kill",
        )
        clients.forEach { it.close() }
        channel.shutdownNow()
        second.close()
    }

    private companion object {
        const val NAMESPACE = "load"
        const val SERVICES = 2_000
        const val MB = 1024.0 * 1024
        const val STOP_SECONDS = 15L
        const val PRELOAD_CONCURRENCY = 64
        const val WRITES = 4_000
        const val WRITERS = 16
        const val OPEN_TIMEOUT_SECONDS = 600L
        const val PROPAGATION_WRITES = 100
        const val DEFAULT_PROPAGATION_PAUSE_MS = 200
        const val P50 = 0.5
        const val P95 = 0.95
        const val P99 = 0.99
        const val NANOS_PER_SECOND = 1e9
        const val MICROS_PER_MILLI = 1000
        const val FAILURE_LENGTH = 90
        const val WINDOW_CLIENTS = 100
        const val DEFAULT_WINDOW = 10_000
        const val WINDOW_SLOW_RATE = 170
        const val WINDOW_FAST_RATE = 1_700
        const val CHURN = 200
        const val TICKS_PER_SECOND = 10
        const val MILLIS_PER_TICK = 100L
        const val MILLIS_PER_SECOND = 1000
        val PROPAGATION_SETTLE = 5.seconds
        val WINDOW_WARM_UP = 5.seconds
        val WINDOW_AFTER_KILL = 15.seconds
        val ENDPOINT: Endpoint = Endpoint.newBuilder().setScheme("http").setHost("10.0.0.1").setPort(8080).build()
    }
}
