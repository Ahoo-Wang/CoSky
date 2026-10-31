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

import io.grpc.Channel
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.ScriptOutputType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.client.CoSkyClient
import me.ahoo.cosky.client.CoSkyClientOptions
import me.ahoo.cosky.server.adapter.grpc.LeaseGrpcService
import me.ahoo.cosky.server.adapter.grpc.ResourceGrpcService
import me.ahoo.cosky.server.adapter.grpc.WatchGrpcService
import me.ahoo.cosky.server.resource.ResourceService
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.cosky.test.conformance.ProtocolConformanceTest
import org.junit.jupiter.api.AfterEach
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val LEASE_TTL = 6.seconds

private fun options(endpoint: String) = CoSkyClientOptions(
    endpoints = listOf(endpoint),
    snapshotDir = null,
    reconnectMin = 20.milliseconds,
    reconnectMax = 200.milliseconds,
    reRegisterJitter = Duration.ZERO,
    leaseTtl = LEASE_TTL,
)

/**
 * The real services on the memory store.
 */
class MemoryProtocolConformanceTest : ProtocolConformanceTest() {
    private val store = MemoryStore(keepRevisions = 64)
    private val name = UUID.randomUUID().toString()
    private val clients = mutableListOf<CoSkyClient>()
    private var scope = newScope()
    private var server = start()
    private val inProcess: ManagedChannel = InProcessChannelBuilder.forName(name).build()

    init {
        runBlocking { store.createNamespace(NamingRules.SYSTEM_NAMESPACE) }
    }

    override val channel: Channel
        get() = inProcess

    private fun newScope() = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)

    private fun start(): Server {
        val caches = WatchCache(store, WINDOW, scope)
        return InProcessServerBuilder.forName(name)
            .addService(ResourceGrpcService(ResourceService(store, caches)))
            .addService(LeaseGrpcService(store))
            .addService(WatchGrpcService(caches, CoSkyProperties()))
            .build()
            .start()
    }

    override fun newClient(): CoSkyClient {
        val options = options("only").copy(channelFactory = { InProcessChannelBuilder.forName(name).build() })
        return CoSkyClient(options).also { clients += it }
    }

    override suspend fun restartServer() {
        server.shutdownNow().awaitTermination(1, TimeUnit.SECONDS)
        scope.cancel()
        scope = newScope()
        server = start()
    }

    override suspend fun loseWritesAfter(namespace: String, revision: Long) {
        store.loseWritesAfter(namespace, revision)
    }

    @AfterEach
    fun stop() {
        clients.forEach { it.close() }
        inProcess.shutdownNow()
        server.shutdownNow()
        scope.cancel()
    }

    private companion object {
        const val WINDOW = 1000
    }
}

/**
 * The whole server on real Redis, over a real port.
 */
class RedisProtocolConformanceTest : ProtocolConformanceTest() {
    private val port = ServerSocket(0).use { it.localPort }
    private val clients = mutableListOf<CoSkyClient>()
    private var server = RunningServer(PROPERTIES, port = port)
    private val remote: ManagedChannel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build()

    override val channel: Channel
        get() = remote

    override fun newClient(): CoSkyClient = CoSkyClient(options("localhost:$port")).also { clients += it }

    override suspend fun restartServer() {
        server.close()
        server = RunningServer(PROPERTIES, port = port)
    }

    /**
     * Rewinds the timeline in place: the real failover is covered by [ServerEndToEndTest].
     */
    override suspend fun loseWritesAfter(namespace: String, revision: Long) {
        val script = """
            local kept = redis.call('XRANGE', KEYS[2], '-', ARGV[1] .. '-9999')
            redis.call('DEL', KEYS[2])
            for _, entry in ipairs(kept) do
              redis.call('XADD', KEYS[2], entry[1], unpack(entry[2]))
            end
            redis.call('SET', KEYS[1], ARGV[1])
            return 1
        """.trimIndent()
        COMMANDS.eval<Long>(
            script,
            ScriptOutputType.INTEGER,
            arrayOf("cosky:{$namespace}:rev", "cosky:{$namespace}:log"),
            revision.toString(),
        ).await()
    }

    @AfterEach
    fun stop() {
        clients.forEach { it.close() }
        remote.shutdownNow()
        server.close()
    }

    private companion object {
        val REDIS: RedisContainer = RedisContainer().also { it.start() }
        val COMMANDS = RedisClient.create(RedisURI.create(REDIS.host, REDIS.getMappedPort(RedisContainer.REDIS_PORT)))
            .connect()
            .async()
        val PROPERTIES: Map<String, Any> = mapOf(
            "spring.main.web-application-type" to "none",
            "cosky.store.redis.uri" to "redis://${REDIS.host}:${REDIS.getMappedPort(RedisContainer.REDIS_PORT)}",
            "cosky.store.redis.tail-poll-interval" to "100ms",
            "cosky.lease.reaper-interval" to "200ms",
            "cosky.lease.reaper-grace" to "0s",
            "cosky.auth.enabled" to "false",
        )
    }
}
