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

package me.ahoo.cosky.client

import io.grpc.inprocess.InProcessChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.v1.endpoint
import me.ahoo.cosky.api.validation.Limits
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A renewal stream that answers nothing is given up on in time to renew the lease before it expires, for every TTL a
 * client may ask for (design D32).
 */
class RenewalSilenceTest {
    private val server = TestServer()
    private var client: CoSkyClient? = null

    @BeforeEach
    fun start() {
        server.start()
        runBlocking { server.store.createNamespace(NAMESPACE) }
    }

    @AfterEach
    fun stop() {
        client?.close()
        server.close()
    }

    @Test
    fun `a silent renewal stream is opened again before the lease expires, whatever the call timeout`() =
        runBlocking<Unit>(Dispatchers.Default) {
            // The default call timeout of 10 s, and the shortest TTL: renewed every 5/3 s, reaped after 20/3 s.
            val options = CoSkyClientOptions(listOf("only")) {
                snapshotDir = null
                reconnectMin = 10.milliseconds
                reconnectMax = 100.milliseconds
                leaseTtl = TTL
                channelFactory = { InProcessChannelBuilder.forName(server.name).build() }
            }
            options.callTimeout.assert().isEqualTo(10.seconds)
            val instances = CoSkyClient(options).also { client = it }.namespace(NAMESPACE).instances
            instances.register("order", ENDPOINT, instanceName = "a")
            withTimeout(10.seconds) { eventually { server.renewalStreams.get() == 1 } }

            server.answerRenewals = false

            // Its last answer came before this, so the lease expires within a TTL from here. Waiting a period and a
            // call's time, 5/3 + 10 s, would see it go.
            withTimeout(TTL) { eventually { server.renewalStreams.get() > 1 } }
        }

    @Test
    fun `a renewal stream is given up on with a period left before the lease expires, for every ttl`() {
        (Limits.DEFAULT.minLeaseTtlSeconds..Limits.DEFAULT.maxLeaseTtlSeconds).forEach { seconds ->
            val ttl = seconds.seconds
            listOf(1.seconds, 10.seconds, 1.hours).forEach { callTimeout ->
                val silence = LeaseKeeper.silenceOf(ttl, callTimeout)
                // Past the next answer, and soon enough to renew while the lease lasts.
                silence.assert().isGreaterThan(ttl / 3)
                (ttl - silence).assert().isGreaterThanOrEqualTo(ttl / 3)
            }
        }
        // As before where that was soon enough.
        LeaseKeeper.silenceOf(30.seconds, 10.seconds).assert().isEqualTo(20.seconds)
        LeaseKeeper.silenceOf(60.seconds, 10.seconds).assert().isEqualTo(30.seconds)
    }

    private suspend fun eventually(condition: () -> Boolean) {
        while (!condition()) {
            delay(10.milliseconds)
        }
    }

    private companion object {
        const val NAMESPACE = "prod"
        val TTL = 5.seconds
        val ENDPOINT = endpoint {
            scheme = "http"
            host = "10.0.0.1"
            port = 8080
        }
    }
}
