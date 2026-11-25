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

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.server.lease.LeaseReaper
import me.ahoo.cosky.store.redis.RedisStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A replica stops within what Kubernetes gives a pod, 25 seconds in the manifests, also while the Dashboard keeps a
 * watch open on it: such a watch never finishes by itself.
 */
class ShutdownTest {
    @Test
    fun `a replica with a REST watch open stops well within a pod's grace period`() {
        val server = RunningServer(mapOf("cosky.store.type" to CoSkyProperties.MEMORY, "cosky.auth.enabled" to "false"))
        val http = HttpClient.newHttpClient()
        val watch = http.sendAsync(
            HttpRequest.newBuilder(
                URI("http://localhost:${server.httpPort}/v1/namespaces/_system/watch?kind=KIND_NAMESPACE")
            )
                .header("Accept", "text/event-stream")
                .build(),
            HttpResponse.BodyHandlers.ofLines(),
        )
        // Open, and past its snapshot.
        watch.get(WAIT_SECONDS, TimeUnit.SECONDS).body().filter { it.startsWith("data:") }.findFirst()

        val started = System.nanoTime()
        server.close()
        val stopped = Duration.ofNanos(System.nanoTime() - started)

        stopped.assert().isLessThan(Duration.ofSeconds(STOP_SECONDS))
    }

    /**
     * Another replica would otherwise wait out the lock's TTL, 15 seconds, before it reaped. The store closes as the
     * server stops, so this also says the reaper lets go before.
     */
    @Test
    fun `a replica that leads the reaper lets go of its lock as it stops`() = runBlocking<Unit> {
        val redis = RedisContainer().apply { start() }
        try {
            val uri = "redis://${redis.host}:${redis.getMappedPort(RedisContainer.REDIS_PORT)}"
            val server = RunningServer(
                mapOf(
                    "cosky.store.redis.uri" to uri,
                    "cosky.auth.enabled" to "false",
                    "cosky.lease.reaper-interval" to "100ms",
                ),
            )
            RedisStore.open(uri).use { another ->
                // Until the server's reaper holds it: taking it here meanwhile gives it back at once.
                while (another.tryLock(LeaseReaper.LOCK, PROBE, 1.seconds)) {
                    another.unlock(LeaseReaper.LOCK, PROBE)
                    delay(50.milliseconds)
                }

                server.close()

                another.tryLock(LeaseReaper.LOCK, PROBE, 1.seconds).assert().isTrue()
            }
        } finally {
            redis.stop()
        }
    }

    private companion object {
        const val PROBE = "another replica"
        const val WAIT_SECONDS = 10L

        /** The 5 seconds of a shutdown phase, and more than enough for the rest, below 25. */
        const val STOP_SECONDS = 15L
    }
}
