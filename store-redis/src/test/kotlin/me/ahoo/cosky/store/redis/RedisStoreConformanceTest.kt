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

import io.lettuce.core.Range
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.endpoint
import me.ahoo.cosky.api.v1.instanceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.store.GrantResult
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.StoreOptions
import me.ahoo.cosky.store.StoreUnavailableException
import me.ahoo.cosky.store.key
import me.ahoo.cosky.test.conformance.StoreConformanceTest
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Rewinds a namespace's timeline in place, to a revision in KEYS[1] (its revision key) and ARGV[1], with its log in
 * KEYS[2]: what a failover that lost the writes after that revision leaves behind, objects aside.
 */
internal val LOSE_WRITES_AFTER: String = """
    local kept = redis.call('XRANGE', KEYS[2], '-', ARGV[1] .. '-9999')
    redis.call('DEL', KEYS[2])
    for _, entry in ipairs(kept) do
      redis.call('XADD', KEYS[2], entry[1], unpack(entry[2]))
    end
    redis.call('SET', KEYS[1], ARGV[1])
    return 1
""".trimIndent()

class RedisStoreConformanceTest : StoreConformanceTest() {
    private val stores = mutableListOf<RedisStore>()

    override fun newStore(options: StoreOptions): Store =
        RedisStore.standalone(CLIENT, options).also { stores += it }

    /**
     * Rewinds the timeline in place. A real failover is covered by [RedisStoreFailoverTest]; here the suite needs an
     * arbitrary revision, and only the timeline matters, so objects stay as they are.
     */
    override suspend fun loseWritesAfter(store: Store, namespace: String, revision: Long) {
        val keys = RedisKeys(namespace)
        CONNECTION.async().eval<Long>(
            LOSE_WRITES_AFTER,
            ScriptOutputType.INTEGER,
            arrayOf(keys.revision, keys.log),
            revision.toString(),
        ).await()
    }

    @Test
    fun `a lease id is not given out again when redis loses the sequence`() = runBlocking<Unit> {
        val namespace = "leases-${UUID.randomUUID()}"
        val store = newStore(StoreOptions())
        store.createNamespace(namespace)
        val first = (store.grantLease(namespace, 30.seconds, "test") as GrantResult.Granted).lease.id
        CONNECTION.async().del(RedisKeys(namespace).leaseSequence).await()
        delay(1100.milliseconds)
        val second = (store.grantLease(namespace, 30.seconds, "test") as GrantResult.Granted).lease.id
        second.assert().isGreaterThan(first)
        second.assert().isLessThan(1L shl 53)
        store.keepAlive(namespace, listOf(second))[second].assert().isNotNull()
    }

    @Test
    fun `the count of the log's bytes stays true through writes, deletes, expiries and trimming`() = runBlocking<Unit> {
        val namespace = "counted-${UUID.randomUUID()}"
        val keys = RedisKeys(namespace)
        val store = newStore(StoreOptions(logSize = 40, logBytes = 30_000))
        store.createNamespace(namespace)
        val lease = (store.grantLease(namespace, 30.seconds, "test") as GrantResult.Granted).lease.id
        repeat(60) {
            val content = "$it".padEnd(1000)
            store.apply(namespace, listOf(Mutation.Put(config(namespace, "a", content))), "test")
            store.apply(namespace, listOf(Mutation.Put(instance(namespace, "i$it", lease))), "test")
            if (it % 5 == 0) {
                store.apply(namespace, listOf(Mutation.Delete(config(namespace, "a", content).key)), "test")
            }
        }
        store.expireLease(namespace, lease, "test", force = true)

        val connection = CLIENT.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE))
        val commands = connection.async()
        val entries = commands.xrange(keys.log, Range.unbounded()).await()
        val actual = entries.sumOf { entry ->
            entry.body.entries.sumOf { (field, value) -> field.length + value.size }.toLong()
        }
        commands.get(keys.logBytes).await().decodeToString().toLong().assert().isEqualTo(actual)
        store.apply(namespace, listOf(Mutation.Delete(config(namespace, "a", "").key)), "test")
        val epoch = checkNotNull(store.position(namespace)).epoch
        store.terminateNamespace(namespace, epoch)
        store.dropNamespace(namespace, epoch)
        commands.exists(keys.logBytes).await().assert().isEqualTo(0L)
        connection.close()
    }

    @Test
    fun `an expiring lease keeps the log within its limits too`() = runBlocking<Unit> {
        val namespace = "expired-${UUID.randomUUID()}"
        val store = newStore(StoreOptions(logSize = 10))
        store.createNamespace(namespace)
        val lease = (store.grantLease(namespace, 30.seconds, "test") as GrantResult.Granted).lease.id
        repeat(8) { store.apply(namespace, listOf(Mutation.Put(instance(namespace, "i$it", lease))), "test") }

        store.expireLease(namespace, lease, "test", force = true)

        CONNECTION.async().xlen(RedisKeys(namespace).log).await().assert().isEqualTo(10)
    }

    @Test
    fun `a log far over a lowered limit goes down over a few writes`() = runBlocking<Unit> {
        val namespace = "lowered-${UUID.randomUUID()}"
        val keys = RedisKeys(namespace)
        val generous = newStore(StoreOptions())
        generous.createNamespace(namespace)
        repeat(500) { generous.apply(namespace, listOf(Mutation.Put(config(namespace, "a", "$it"))), "test") }
        val strict = newStore(StoreOptions(logSize = 10))

        strict.apply(namespace, listOf(Mutation.Put(config(namespace, "a", "one"))), "test")
        CONNECTION.async().xlen(keys.log).await().assert().isGreaterThan(400)

        repeat(10) { strict.apply(namespace, listOf(Mutation.Put(config(namespace, "a", "$it!"))), "test") }
        CONNECTION.async().xlen(keys.log).await().assert().isEqualTo(10)
    }

    private fun config(namespace: String, name: String, content: String): Resource = resource {
        meta = objectMeta {
            this.namespace = namespace
            this.name = name
        }
        config = configSpec { this.content = content }
    }

    private fun instance(namespace: String, name: String, lease: Long): Resource = resource {
        meta = objectMeta {
            this.namespace = namespace
            this.name = name
            this.lease = lease
        }
        instance = instanceSpec {
            service = "order"
            endpoint = endpoint {
                scheme = "http"
                host = "10.0.0.1"
                port = 8080
            }
        }
    }

    /**
     * A removal checks each batch against the epoch it is for, in the script: one that comes late must not take
     * anything of the namespace created since, and the epoch goes last, so that a removal cut short can go on.
     */
    @Test
    fun `removing data stops at a namespace that is active again, or another epoch`() = runBlocking<Unit> {
        val keys = RedisKeys("removal-${UUID.randomUUID()}")
        val connection = CLIENT.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE))
        val commands = connection.async() as RedisClusterAsyncCommands<String, ByteArray>
        val remove = RedisScript("namespace_remove")
        suspend fun removeEpoch(epoch: String) =
            remove.run<Long>(
                commands,
                ScriptOutputType.INTEGER,
                listOf(keys.state, keys.epoch, keys.epoch),
                listOf(epoch.arg())
            )
        commands.set(keys.epoch, "epoch".arg()).await()

        commands.set(keys.state, "active".arg()).await()
        removeEpoch("epoch")
        commands.exists(keys.epoch).await().assert().isEqualTo(1)

        commands.set(keys.state, "terminating".arg()).await()
        removeEpoch("another")
        commands.exists(keys.epoch).await().assert().isEqualTo(1)
        removeEpoch("epoch")
        commands.exists(keys.epoch).await().assert().isEqualTo(0)
        connection.close()
    }

    @Test
    fun `closing a store closes both connections it opened`() {
        val before = clients()
        val store = RedisStore.standalone(CLIENT)
        clients().assert().isEqualTo(before + 2)

        store.close()

        // Redis drops a connection from its list a moment after it closes.
        val deadline = System.nanoTime() + 5.seconds.inWholeNanoseconds
        while (clients() != before && System.nanoTime() < deadline) {
            Thread.sleep(50)
        }
        clients().assert().isEqualTo(before)
    }

    @Test
    fun `a redis out of reach is the store being unavailable, whether for a command or a script`() {
        val store = RedisStore.standalone(CLIENT)
        store.close()

        val failures = runBlocking {
            listOf(
                runCatching { store.position("unreachable") },
                runCatching { store.createNamespace("unreachable") },
            ).map { it.exceptionOrNull() }
        }

        failures.forEach { it.assert().isInstanceOf(StoreUnavailableException::class.java) }
    }

    private fun clients(): Int = CONNECTION.sync().clientList().lines().count { it.isNotBlank() }

    @AfterEach
    fun closeStores() {
        stores.forEach { it.close() }
    }

    private class RedisContainer : GenericContainer<RedisContainer>(REDIS_IMAGE)

    private companion object {
        val REDIS: RedisContainer = RedisContainer().withExposedPorts(6379).also { it.start() }
        val CLIENT: RedisClient = RedisClient.create(RedisURI.create(REDIS.host, REDIS.getMappedPort(6379)))
        val CONNECTION = CLIENT.connect()
    }
}
