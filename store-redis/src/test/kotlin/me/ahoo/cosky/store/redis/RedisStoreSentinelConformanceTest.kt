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
import io.lettuce.core.ScriptOutputType
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.StoreOptions
import me.ahoo.cosky.store.redis.SentinelRedis.Companion.MASTER
import me.ahoo.cosky.store.redis.SentinelRedis.Companion.SENTINELS
import me.ahoo.cosky.test.conformance.StoreConformanceTest
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * The Store's suite through Sentinel, on the connection that follows the master the Sentinels name.
 */
class RedisStoreSentinelConformanceTest : StoreConformanceTest() {
    private val stores = mutableListOf<RedisStore>()

    override fun newStore(options: StoreOptions): Store =
        RedisStore.sentinel(CLIENT, RedisStore.redisUri(REDIS.uri), options).also { stores += it }

    /** As [RedisStoreConformanceTest] does it, on the master. */
    override suspend fun loseWritesAfter(store: Store, namespace: String, revision: Long) {
        val keys = RedisKeys(namespace)
        REDIS.node(MASTER) {
            eval<Long>(
                LOSE_WRITES_AFTER,
                ScriptOutputType.INTEGER,
                arrayOf(keys.revision, keys.log),
                revision.toString()
            )
        }
    }

    @Test
    fun `closing a store leaves no connection of its own on the master or the Sentinels`() {
        val ports = listOf(MASTER) + SENTINELS
        val before = ports.associateWith(::clients)
        val store = RedisStore.sentinel(CLIENT, RedisStore.redisUri(REDIS.uri))
        clients(MASTER).assert().isGreaterThan(before.getValue(MASTER))

        store.close()

        // Redis drops a connection from its list a moment after it closes.
        val deadline = System.nanoTime() + 5.seconds.inWholeNanoseconds
        while (ports.any { clients(it) != before.getValue(it) } && System.nanoTime() < deadline) {
            Thread.sleep(50)
        }
        ports.associateWith(::clients).assert().isEqualTo(before)
    }

    private fun clients(port: Int): Int = REDIS.node(port) { clientList() }.lines().count { it.isNotBlank() } - 1

    @AfterEach
    fun closeStores() {
        stores.forEach { it.close() }
    }

    private companion object {
        val REDIS = SentinelRedis()
        val CLIENT: RedisClient = RedisClient.create(REDIS.resources)

        @JvmStatic
        @AfterAll
        fun stop() {
            CLIENT.shutdown()
            REDIS.close()
        }
    }
}
