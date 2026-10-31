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

import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.ManagedChannel
import io.grpc.MethodDescriptor
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class ConnectionTest {
    private class CountingChannel(val endpoint: String) : ManagedChannel() {
        var backoffResets = 0
        var shutDown = false

        override fun resetConnectBackoff() {
            backoffResets += 1
        }

        override fun shutdown(): ManagedChannel = apply { shutDown = true }

        override fun isShutdown(): Boolean = shutDown

        override fun isTerminated(): Boolean = shutDown

        override fun shutdownNow(): ManagedChannel = shutdown()

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true

        override fun <Req : Any?, Resp : Any?> newCall(
            method: MethodDescriptor<Req, Resp>,
            options: CallOptions,
        ): ClientCall<Req, Resp> = error("Not for calling.")

        override fun authority(): String = endpoint
    }

    private val channels = mutableListOf<CountingChannel>()

    private fun connection(vararg endpoints: String) = Connection(
        CoSkyClientOptions(
            endpoints = endpoints.toList(),
            channelFactory = { endpoint -> CountingChannel(endpoint).also { channels += it } },
        ),
    )

    @Test
    fun `one endpoint tells its channel not to wait out its own backoff`() {
        val connection = connection("only")
        val channel = connection.channel()
        connection.rotate(channel)
        channels.single().backoffResets.assert().isEqualTo(1)
        connection.channel().assert().isSameAs(channel)
    }

    @Test
    fun `several endpoints move on to the next one`() {
        val connection = connection("first", "second")
        val channel = connection.channel()
        connection.rotate(channel)
        channels.map { it.endpoint }.assert().containsExactly("first", "second")
        channels.first().isShutdown.assert().isTrue()
        connection.channel().assert().isNotSameAs(channel)
    }

    @Test
    fun `a failure someone else already handled changes nothing`() {
        val connection = connection("first", "second")
        val stale = connection.channel()
        connection.rotate(stale)
        val current = connection.channel()
        connection.rotate(stale)
        connection.channel().assert().isSameAs(current)
        channels.size.assert().isEqualTo(2)
    }
}
