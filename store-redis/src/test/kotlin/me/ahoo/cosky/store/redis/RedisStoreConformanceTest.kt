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
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.store.GrantResult
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.StoreOptions
import me.ahoo.cosky.test.conformance.StoreConformanceTest
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
        val script = """
            local kept = redis.call('XRANGE', KEYS[2], '-', ARGV[1] .. '-9999')
            redis.call('DEL', KEYS[2])
            for _, entry in ipairs(kept) do
              redis.call('XADD', KEYS[2], entry[1], unpack(entry[2]))
            end
            redis.call('SET', KEYS[1], ARGV[1])
            return 1
        """.trimIndent()
        CONNECTION.async().eval<Long>(
            script,
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

    /**
     * Two replicas apart have nothing to wait on, so this is the step they race on: what another replica started
     * removing must not take the epoch and log of the namespace created since.
     */
    @Test
    fun `removing data stops at a namespace that is active again`() = runBlocking<Unit> {
        val keys = RedisKeys("removal-${UUID.randomUUID()}")
        val connection = CLIENT.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE))
        val commands = connection.async() as RedisClusterAsyncCommands<String, ByteArray>
        val remove = RedisScript("namespace_remove")
        commands.set(keys.epoch, "epoch".arg()).await()

        commands.set(keys.state, "active".arg()).await()
        remove.run<Long>(commands, ScriptOutputType.INTEGER, listOf(keys.state, keys.epoch), emptyList())
        commands.exists(keys.epoch).await().assert().isEqualTo(1)

        commands.set(keys.state, "terminating".arg()).await()
        remove.run<Long>(commands, ScriptOutputType.INTEGER, listOf(keys.state, keys.epoch), emptyList())
        commands.exists(keys.epoch).await().assert().isEqualTo(0)
        connection.close()
    }

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
