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
import kotlinx.coroutines.future.await
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.StoreOptions
import me.ahoo.cosky.test.conformance.StoreConformanceTest
import org.junit.jupiter.api.AfterEach
import org.testcontainers.containers.GenericContainer

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

    @AfterEach
    fun closeStores() {
        stores.forEach { it.close() }
    }

    private class RedisContainer : GenericContainer<RedisContainer>("redis:7.4-alpine")

    private companion object {
        val REDIS: RedisContainer = RedisContainer().withExposedPorts(6379).also { it.start() }
        val CLIENT: RedisClient = RedisClient.create(RedisURI.create(REDIS.host, REDIS.getMappedPort(6379)))
        val CONNECTION = CLIENT.connect()
    }
}
