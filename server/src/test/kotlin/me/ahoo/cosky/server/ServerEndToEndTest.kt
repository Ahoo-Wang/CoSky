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

import io.grpc.Status
import io.grpc.StatusException
import io.lettuce.core.internal.HostAndPort
import io.lettuce.core.resource.ClientResources
import io.lettuce.core.resource.DnsResolvers
import io.lettuce.core.resource.MappingSocketAddressResolver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.v1.KeepAliveRequest
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.WatchResponse
import me.ahoo.cosky.api.v1.getRequest
import me.ahoo.cosky.api.v1.grantRequest
import me.ahoo.cosky.api.v1.resourceKey
import me.ahoo.cosky.server.auth.Tokens
import me.ahoo.cosky.store.key
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.Network
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The integration check of roadmap W2c: a real server, real Redis, a real client over gRPC.
 */
class ServerEndToEndTest {
    private fun properties(redis: RedisContainer, grace: String = "0s") = mapOf(
        "spring.main.web-application-type" to "none",
        "cosky.store.redis.uri" to "redis://${redis.host}:${redis.getMappedPort(RedisContainer.REDIS_PORT)}",
        "cosky.store.redis.tail-poll-interval" to "100ms",
        "cosky.lease.reaper-interval" to "200ms",
        "cosky.lease.reaper-grace" to grace,
        "cosky.auth.enabled" to "false",
    )

    private fun scenario(timeout: Duration = 60.seconds, block: suspend CoroutineScope.() -> Unit) =
        runBlocking<Unit>(Dispatchers.Default) { withTimeout(timeout, block) }

    private fun <T> withRedis(block: (RedisContainer) -> T): T {
        val redis = RedisContainer()
        redis.start()
        return try {
            block(redis)
        } finally {
            redis.stop()
        }
    }

    @Test
    fun `the bootstrap token signs in, and nothing else does`() = withRedis { redis ->
        val token = Tokens.generate()
        val secured = properties(redis) + mapOf(
            "cosky.auth.enabled" to "true",
            "cosky.auth.bootstrap-token" to token.plaintext,
        )
        RunningServer(secured, token = token.plaintext).use { server ->
            scenario {
                server.createNamespace("secured")
                server.putConfig("secured", "a", "1")
                val request = getRequest {
                    key = RunningServer.config("secured", "a", "1").key
                    minRevision = 1
                }
                server.resources.get(request).meta.updatedBy.assert().isEqualTo("serviceaccount:admin")
                val anonymous = runCatching { server.anonymousResources.get(request) }.exceptionOrNull()
                (anonymous as StatusException).status.code.assert().isEqualTo(Status.Code.UNAUTHENTICATED)
            }
        }
    }

    @Test
    fun `the default namespace exists from the first start on`() = withRedis { redis ->
        RunningServer(properties(redis)).use { server ->
            scenario {
                server.putConfig("default", "a", "1")
            }
        }
    }

    @Test
    fun `a write reaches a watcher, and a get reads it back`() = withRedis { redis ->
        RunningServer(properties(redis)).use { server ->
            scenario {
                server.createNamespace(NAMESPACE)
                val watching = async { server.watch(NAMESPACE, Kind.KIND_CONFIG).take(2).toList() }
                delay(SETTLE)
                server.putConfig(NAMESPACE, "application.yaml", "a: 1")
                val (snapshot, events) = watching.await()
                snapshot.snapshot.itemsList.assert().isEmpty()
                snapshot.snapshot.epoch.assert().isNotEmpty()
                events.events.eventsList.single().resource.config.content.assert().isEqualTo("a: 1")
                val read = server.resources.get(
                    getRequest {
                        key = resourceKey {
                            namespace = NAMESPACE
                            kind = Kind.KIND_CONFIG
                            name = "application.yaml"
                        }
                        minRevision = events.events.revision
                    },
                )
                read.meta.revision.assert().isEqualTo(events.events.revision)
            }
        }
    }

    @Test
    fun `a watcher that goes away resumes without missing anything`() = withRedis { redis ->
        RunningServer(properties(redis)).use { server ->
            scenario {
                server.createNamespace(NAMESPACE)
                server.putConfig(NAMESPACE, "a", "1")
                val snapshot = server.watch(NAMESPACE, Kind.KIND_CONFIG).first().snapshot
                server.putConfig(NAMESPACE, "b", "2")
                val resumed = server.watch(
                    NAMESPACE,
                    Kind.KIND_CONFIG,
                    fromRevision = snapshot.revision,
                    fromEpoch = snapshot.epoch,
                ).first { it.hasEvents() }
                resumed.events.eventsList.single().resource.meta.name.assert().isEqualTo("b")
            }
        }
    }

    @Test
    fun `an expired lease removes its instance`() = withRedis { redis ->
        RunningServer(properties(redis)).use { server ->
            scenario {
                server.createNamespace(NAMESPACE)
                val lease = server.leases.grant(
                    grantRequest {
                        namespace = NAMESPACE
                        ttlSeconds = MIN_TTL_SECONDS
                    },
                ).lease
                server.putInstance(NAMESPACE, "order-1", lease)
                val removed = server.watch(NAMESPACE, Kind.KIND_INSTANCE).first {
                    it.hasEvents() && it.events.eventsList.single().type == WatchResponse.Event.Type.TYPE_DELETE
                }
                removed.events.eventsList.single().resource.meta.name.assert().isEqualTo("order-1")
            }
        }
    }

    /**
     * The worst case of design §6.2: Redis is gone for longer than the TTL, and the client only comes back a little
     * after it, as a reconnect backoff does. Its lease looks expired to the reaper, yet the process is alive.
     */
    @Test
    fun `an outage longer than the ttl does not delete live instances`() = withRedis { redis ->
        RunningServer(properties(redis, grace = "3s")).use { server ->
            scenario(120.seconds) {
                server.createNamespace(NAMESPACE)
                delay(REAPER_PAST_GRACE)
                val lease = server.leases.grant(
                    grantRequest {
                        namespace = NAMESPACE
                        ttlSeconds = MIN_TTL_SECONDS
                    },
                ).lease
                server.putInstance(NAMESPACE, "order-1", lease)
                val seen = CopyOnWriteArrayList<WatchResponse>()
                val watcher = launch { server.watch(NAMESPACE, Kind.KIND_INSTANCE, seen).toList() }
                val renewing = launch { keepRenewing(server, lease) }
                delay(RENEWING_FOR_A_WHILE)

                // A renewal must not be queued at Redis, or it would run first on recovery and save the lease anyway.
                renewing.cancel()
                val docker = redis.dockerClient
                docker.pauseContainerCmd(redis.containerId).exec()
                delay(OUTAGE)
                docker.unpauseContainerCmd(redis.containerId).exec()

                delay(RECONNECT_BACKOFF)
                val renewingAgain = launch { keepRenewing(server, lease) }
                delay(AFTER_RECOVERY)
                renewingAgain.cancel()
                watcher.cancel()
                seen.filter { it.hasEvents() }.flatMap { it.events.eventsList }
                    .filter { it.type == WatchResponse.Event.Type.TYPE_DELETE }
                    .assert().isEmpty()
            }
        }
    }

    /**
     * A client that survives server and store failures: failing to renew is not losing the lease (design §6.2).
     */
    private suspend fun keepRenewing(server: RunningServer, lease: Long) {
        val renewals: Flow<KeepAliveRequest> = flow {
            while (true) {
                emit(KeepAliveRequest.newBuilder().setNamespace(NAMESPACE).setLease(lease).build())
                delay(RENEW_EVERY)
            }
        }
        server.leases.keepAlive(renewals).collect {
            check(it.ttlSeconds > 0) { "The lease was lost." }
        }
    }

    /**
     * Real lost writes: a replica falls behind and takes over, and the server's connections move to it. The watcher
     * must learn about it, and the new timeline must be readable.
     */
    @Test
    fun `lost writes reach the client as gone, and a new watch converges`() {
        Network.newNetwork().use { network ->
            val primary = RedisContainer("redis-server", "--repl-diskless-sync-delay", "0")
                .withNetwork(network)
                .withNetworkAliases("primary")
            val replica = RedisContainer("redis-server", "--replicaof", "primary", "6379").withNetwork(network)
            primary.start()
            replica.start()
            try {
                FailoverAddress.port.set(primary.getMappedPort(RedisContainer.REDIS_PORT))
                FailoverAddress.host = primary.host
                RunningServer(properties(primary), FailoverAddress::class.java).use {
                    runFailover(it, primary, replica)
                }
            } finally {
                replica.stop()
                primary.stop()
            }
        }
    }

    private fun runFailover(server: RunningServer, primary: RedisContainer, replica: RedisContainer) = scenario {
        server.createNamespace(NAMESPACE)
        server.putConfig(NAMESPACE, "kept", "1")
        val seen = CopyOnWriteArrayList<WatchResponse>()
        val first = CompletableDeferred<Unit>()
        val gone = CompletableDeferred<Unit>()
        val watcher = launch {
            server.watch(NAMESPACE, Kind.KIND_CONFIG, seen).collect {
                if (it.hasSnapshot()) first.complete(Unit)
                if (it.hasGone()) gone.complete(Unit)
            }
        }
        first.await()
        val oldEpoch = seen.first().snapshot.epoch
        waitForReplica(primary, replica)
        check(replica.execInContainer("redis-cli", "REPLICAOF", "NO", "ONE").exitCode == 0)
        server.putConfig(NAMESPACE, "lost", "2")
        delay(SETTLE)
        FailoverAddress.port.set(replica.getMappedPort(RedisContainer.REDIS_PORT))
        primary.execInContainer("redis-cli", "CLIENT", "KILL", "TYPE", "normal")
        primary.execInContainer("redis-cli", "CLIENT", "KILL", "TYPE", "pubsub")
        gone.await()
        watcher.cancel()

        val again = server.watch(NAMESPACE, Kind.KIND_CONFIG).first { it.hasSnapshot() }.snapshot
        again.epoch.assert().isNotEqualTo(oldEpoch)
        again.itemsList.map { it.meta.name }.assert().containsExactly("kept")
    }

    private suspend fun waitForReplica(primary: RedisContainer, replica: RedisContainer) {
        while (offset(primary, "master_repl_offset") != offset(replica, "slave_repl_offset")) {
            delay(SETTLE)
        }
    }

    private fun offset(container: RedisContainer, field: String): String? =
        container.execInContainer("redis-cli", "INFO", "replication").stdout.lineSequence()
            .firstOrNull { it.startsWith("$field:") }
            ?.substringAfter(':')
            ?.trim()

    /**
     * Moves every connection to whichever node [port] names, as a virtual IP does in a failover. Not a
     * `@Configuration`: component scanning would give it to every test.
     */
    class FailoverAddress {
        @Bean
        fun clientResources(): ClientResources = ClientResources.builder()
            .socketAddressResolver(
                MappingSocketAddressResolver.create(DnsResolvers.UNRESOLVED) {
                    HostAndPort.of(host, port.get())
                },
            )
            .build()

        companion object {
            val port = AtomicInteger()

            @Volatile
            var host: String = "localhost"
        }
    }

    private companion object {
        const val NAMESPACE = "prod"
        const val MIN_TTL_SECONDS = 5
        val SETTLE = 200.milliseconds
        val RENEW_EVERY = 1.seconds
        val REAPER_PAST_GRACE = 4.seconds
        val RENEWING_FOR_A_WHILE = 2.seconds
        val OUTAGE = 8.seconds
        val RECONNECT_BACKOFF = 1.seconds
        val AFTER_RECOVERY = 6.seconds
    }
}
