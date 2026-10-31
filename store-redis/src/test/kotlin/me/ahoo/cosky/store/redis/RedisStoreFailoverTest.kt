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

package me.ahoo.cosky.store.redis

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.internal.HostAndPort
import io.lettuce.core.resource.ClientResources
import io.lettuce.core.resource.DnsResolvers
import io.lettuce.core.resource.MappingSocketAddressResolver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.Position
import me.ahoo.cosky.store.TimelineDivergedException
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Real lost writes (design §6.4): a replica falls behind and takes over, as after a failover, and the store's
 * connections move to it. Nothing here touches the store's keys by hand.
 */
class RedisStoreFailoverTest {
    private val network = Network.newNetwork()
    private val primary = RedisContainer()
        .withNetwork(network)
        .withNetworkAliases(PRIMARY_ALIAS)
        .withCommand("redis-server", "--repl-diskless-sync-delay", "0")
    private val replica = RedisContainer()
        .withNetwork(network)
        .withCommand("redis-server", "--replicaof", PRIMARY_ALIAS, REDIS_PORT.toString())
    private val port = AtomicInteger()
    private lateinit var client: RedisClient
    private lateinit var store: RedisStore

    @BeforeEach
    fun start() {
        primary.start()
        replica.start()
        port.set(primary.getMappedPort(REDIS_PORT))
        val resources = ClientResources.builder()
            .socketAddressResolver(
                MappingSocketAddressResolver.create(DnsResolvers.UNRESOLVED) {
                    HostAndPort.of(primary.host, port.get())
                },
            )
            .build()
        client = RedisClient.create(resources, RedisURI.create(primary.host, primary.getMappedPort(REDIS_PORT)))
        store = RedisStore.standalone(client, tailPollInterval = 50.milliseconds)
    }

    @AfterEach
    fun stop() {
        store.close()
        client.shutdown()
        replica.stop()
        primary.stop()
        network.close()
    }

    private fun config(value: String) = resource {
        meta = objectMeta {
            namespace = NAMESPACE
            name = "application.yaml"
        }
        config = configSpec { content = value }
    }

    private suspend fun put(value: String) {
        store.apply(NAMESPACE, listOf(Mutation.Put(config(value))), "test")
    }

    /**
     * Replicates everything so far, detaches the replica, and lets [lost] reach only the primary. Then the replica
     * becomes the node every connection talks to.
     */
    private suspend fun failOverLosing(lost: suspend () -> Unit) {
        waitForReplica()
        check(replica.execInContainer("redis-cli", "REPLICAOF", "NO", "ONE").exitCode == 0)
        lost()
        port.set(replica.getMappedPort(REDIS_PORT))
        primary.execInContainer("redis-cli", "CLIENT", "KILL", "TYPE", "normal")
        primary.execInContainer("redis-cli", "CLIENT", "KILL", "TYPE", "pubsub")
    }

    /**
     * Compares replication offsets: a WAIT from a fresh connection says nothing about other connections' writes.
     */
    private suspend fun waitForReplica() {
        withTimeout(TIMEOUT) {
            while (replication(primary, "master_repl_offset") != replication(replica, "slave_repl_offset")) {
                delay(50.milliseconds)
            }
        }
    }

    private fun replication(container: RedisContainer, field: String): String? =
        container.execInContainer("redis-cli", "INFO", "replication").stdout.lineSequence()
            .firstOrNull { it.startsWith("$field:") }
            ?.substringAfter(':')
            ?.trim()

    private suspend fun follow(start: Position, caughtUp: CompletableDeferred<Unit>, revision: Long) {
        store.changes(NAMESPACE, start).collect {
            if (it.revision == revision) {
                caughtUp.complete(Unit)
            }
        }
    }

    @Test
    fun `a follower finds the revision that went back after a failover`() = runBlocking<Unit> {
        withTimeout(TIMEOUT) {
            val start = store.createNamespace(NAMESPACE)
            put("kept")
            put("lost")
            val caughtUp = CompletableDeferred<Unit>()
            val follower = async { runCatching { follow(start, caughtUp, 3) }.exceptionOrNull() }
            failOverLosing {
                put("lost as well")
                caughtUp.await()
            }
            val failure = follower.await() as TimelineDivergedException
            failure.current.epoch.assert().isNotEqualTo(start.epoch)
            failure.current.revision.assert().isEqualTo(2)
            store.readAll(NAMESPACE).toList().single().config.content.assert().isEqualTo("lost")
        }
    }

    @Test
    fun `a follower finds a revision reused by different content after a failover`() = runBlocking<Unit> {
        withTimeout(TIMEOUT) {
            val start = store.createNamespace(NAMESPACE)
            put("kept")
            val caughtUp = CompletableDeferred<Unit>()
            val follower = async { runCatching { follow(start, caughtUp, 2) }.exceptionOrNull() }
            failOverLosing {
                put("lost")
                caughtUp.await()
            }
            put("written after the failover")
            val failure = follower.await() as TimelineDivergedException
            failure.current.epoch.assert().isNotEqualTo(start.epoch)
            failure.current.revision.assert().isGreaterThanOrEqualTo(1)
        }
    }

    private class RedisContainer : GenericContainer<RedisContainer>("redis:7.4-alpine") {
        init {
            withExposedPorts(REDIS_PORT)
        }
    }

    private companion object {
        const val REDIS_PORT = 6379
        const val PRIMARY_ALIAS = "primary"
        const val NAMESPACE = "failover"
        val TIMEOUT = 60.seconds
    }
}
