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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.store.ChangeBatch
import me.ahoo.cosky.store.Mutation
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * A follower reads the log once for each write it is told of: a read that comes short of a batch has reached the end
 * of the log, and the hint of the next write wakes it. Reading again after each would add a read of every namespace
 * written, on every replica, for every write.
 */
class RedisStoreTailTest {
    private class RedisContainer : GenericContainer<RedisContainer>(REDIS_IMAGE) {
        init {
            withExposedPorts(REDIS_PORT)
        }
    }

    private fun write(content: String) = listOf(
        Mutation.Put(
            resource {
                meta = objectMeta {
                    namespace = NAMESPACE
                    name = "application.yaml"
                }
                config = configSpec { this.content = content }
            },
        ),
    )

    @Test
    fun `a follower that has read to the end of the log waits for the next hint`() = runBlocking<Unit> {
        RedisContainer().use { redis ->
            redis.start()
            val counter = RedisClient.create(RedisURI.create(redis.host, redis.getMappedPort(REDIS_PORT)))
            try {
                counter.connect().use { connection ->
                    fun scripts(): Long = connection.sync().info("commandstats").lineSequence()
                        .filter { it.startsWith("cmdstat_evalsha:") || it.startsWith("cmdstat_eval:") }
                        .sumOf { it.substringAfter("calls=").substringBefore(',').toLong() }

                    // No poll of its own in the time this takes: only hints wake the follower.
                    val uri = "redis://${redis.host}:${redis.getMappedPort(REDIS_PORT)}"
                    RedisStore.open(uri, tailPollInterval = 1.minutes).use { store ->
                        val from = store.createNamespace(NAMESPACE)
                        val received = Channel<ChangeBatch>(Channel.UNLIMITED)
                        val following = launch { store.changes(NAMESPACE, from).collect { received.send(it) } }
                        // The scripts are loaded by the first write and the first reads.
                        store.apply(NAMESPACE, write("0"), "test")
                        received.receive()
                        delay(SETTLE)

                        val before = scripts()
                        repeat(WRITES) {
                            store.apply(NAMESPACE, write("${it + 1}"), "test")
                            received.receive()
                        }
                        delay(SETTLE)

                        // Each write, and one read of it.
                        (scripts() - before).assert().isEqualTo(2L * WRITES)
                        following.cancel()
                    }
                }
            } finally {
                counter.shutdown()
            }
        }
    }

    private companion object {
        const val NAMESPACE = "tail"
        const val WRITES = 10
        const val REDIS_PORT = 6379
        val SETTLE = 500.milliseconds
    }
}
