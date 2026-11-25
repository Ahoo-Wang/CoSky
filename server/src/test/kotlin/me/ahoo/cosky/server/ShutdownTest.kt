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

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit

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

    private companion object {
        const val WAIT_SECONDS = 10L

        /** The 5 seconds of a shutdown phase, and more than enough for the rest, below 25. */
        const val STOP_SECONDS = 15L
    }
}
