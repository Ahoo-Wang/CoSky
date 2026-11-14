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
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.output.StatusOutput
import io.lettuce.core.protocol.CommandArgs
import io.lettuce.core.protocol.CommandType
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.store.ApplyResult
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.redis.SentinelRedis.Companion.MASTER
import me.ahoo.cosky.store.redis.SentinelRedis.Companion.REPLICA
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * A master that hangs is replaced by its replica once the Sentinels agree it is down, and the store follows it: the
 * namespace takes writes again within seconds, rather than once the old master answers again.
 */
class RedisStoreSentinelFailoverTest {
    private fun config(namespace: String, content: String) = resource {
        meta = objectMeta {
            this.namespace = namespace
            name = "application.yaml"
        }
        config = configSpec { this.content = content }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `a hung master's namespaces take writes again once Sentinel promotes its replica`() = runBlocking<Unit> {
        REDIS.awaitReady()
        RedisStore.open(REDIS.uri, resources = REDIS.resources).use { store ->
            val namespace = "ns-${UUID.randomUUID()}"
            store.createNamespace(namespace)
            store.apply(namespace, listOf(Mutation.Put(config(namespace, "before"))), "test")
            // The replica that takes over holds the namespace, so that only not following it can fail the writes.
            while (REDIS.node(REPLICA) { dbsize() } < REDIS.node(MASTER) { dbsize() }) {
                delay(RETRY)
            }
            // Hung rather than gone: it answers nothing and its connections stay open, as with a host that vanished.
            // Sent on a connection of its own, which this leaves waiting.
            val hung = RedisClient.create(REDIS.resources, RedisURI.create("127.0.0.1", MASTER))
            try {
                hung.connect().async().dispatch(
                    CommandType.DEBUG,
                    StatusOutput(StringCodec.UTF8),
                    CommandArgs(StringCodec.UTF8).add("SLEEP").add(HANG.inWholeSeconds),
                )
                val after = listOf(Mutation.Put(config(namespace, "after")))
                val failed = TimeSource.Monotonic.markNow()
                // What the last attempt gave, a result or a failure, or null when it took too long.
                var last: Any? = null
                while (last !is ApplyResult.Applied && failed.elapsedNow() < RECOVERY) {
                    last = withTimeoutOrNull(ATTEMPT) {
                        runCatching { store.apply(namespace, after, "test") }.fold({ it }, { it })
                    }
                    if (last !is ApplyResult.Applied) {
                        delay(RETRY)
                    }
                }

                last.assert().isInstanceOf(ApplyResult.Applied::class.java)
            } finally {
                hung.shutdown(Duration.ZERO, Duration.ZERO)
            }
        }
    }

    private companion object {
        /**
         * The Sentinels' down-after time, their vote and the promotion, then the store reading their announcement,
         * with a wait of 5 seconds at most for the hung master before it: about 15 seconds, with room for a slow
         * runner. Without following the Sentinels, never, while the master hangs.
         */
        val RECOVERY = 45.seconds
        val ATTEMPT = 3.seconds
        val RETRY = 200.milliseconds

        /** Longer than [RECOVERY], so that only following the failover can make the store take writes again. */
        val HANG = 90.seconds

        val REDIS = SentinelRedis()

        @JvmStatic
        @AfterAll
        fun stop() = REDIS.close()
    }
}
