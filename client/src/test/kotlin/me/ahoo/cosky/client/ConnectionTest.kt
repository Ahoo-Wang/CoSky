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
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import me.ahoo.cosky.api.v1.LeaseServiceGrpc
import me.ahoo.cosky.api.v1.RevokeRequest
import me.ahoo.cosky.api.v1.RevokeResponse
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.UncheckedIOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class ConnectionTest {
    private class CountingChannel(val endpoint: String) : ManagedChannel() {
        var shutDown = false

        /** Calls started on it, which go nowhere. */
        var calls = 0

        /** Of the calls started on it, those cancelled. */
        var cancelled = 0

        /**
         * Runs while the channel shuts down, which is when the connection has let go of its lock.
         */
        var onShutdown: () -> Unit = {}

        override fun shutdown(): ManagedChannel = apply {
            if (!shutDown) {
                shutDown = true
                onShutdown()
            }
        }

        override fun isShutdown(): Boolean = shutDown

        override fun isTerminated(): Boolean = shutDown

        override fun shutdownNow(): ManagedChannel = shutdown()

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true

        override fun <Req : Any?, Resp : Any?> newCall(
            method: MethodDescriptor<Req, Resp>,
            options: CallOptions,
        ): ClientCall<Req, Resp> = object : ClientCall<Req, Resp>() {
            override fun start(responseListener: Listener<Resp>, headers: Metadata) {
                calls += 1
            }

            override fun request(numMessages: Int) = Unit

            override fun cancel(message: String?, cause: Throwable?) {
                cancelled += 1
            }

            override fun halfClose() = Unit

            override fun sendMessage(message: Req) = Unit
        }

        override fun authority(): String = endpoint
    }

    private val channels = mutableListOf<CountingChannel>()

    /**
     * Runs as each channel after the first is built, which is when a rotation has let go of its lock.
     */
    private var building: () -> Unit = {}

    private fun connection(vararg endpoints: String) = Connection(
        CoSkyClientOptions(endpoints.toList()) {
            channelFactory = { endpoint ->
                if (channels.isNotEmpty()) {
                    building()
                }
                CountingChannel(endpoint).also { channels += it }
            }
        },
    )

    @Test
    fun `one endpoint moves on to a new channel to it, which may reach another replica`() {
        val connection = connection("only")
        val channel = connection.channel()
        connection.rotate(channel)
        channels.map { it.endpoint }.assert().containsExactly("only", "only")
        channels.map { it.isShutdown }.assert().containsExactly(true, false)
        connection.channel().assert().isNotSameAs(channel)
    }

    private fun Channel.call(): ClientCall<RevokeRequest, RevokeResponse> =
        newCall(LeaseServiceGrpc.getRevokeMethod(), CallOptions.DEFAULT)

    private fun ClientCall<RevokeRequest, RevokeResponse>.start() =
        start(object : ClientCall.Listener<RevokeResponse>() {}, Metadata())

    @Test
    fun `a call started on a channel that was moved on from goes to the one that took over`() {
        val connection = connection("only")
        // Taken for a registration, say, just before a stream failed.
        val taken = connection.channel()
        connection.rotate(taken)
        taken.call().start()
        channels.map { it.calls }.assert().containsExactly(0, 1)
    }

    @Test
    fun `a call made before the move, and started after it, goes to the one that took over`() {
        val connection = connection("only")
        // As a stub makes a call and starts it a moment later, while a failing stream moves on in between.
        val call = connection.channel().call()
        connection.rotate(connection.channel())
        call.start()
        channels.map { it.calls }.assert().containsExactly(0, 1)
    }

    @Test
    fun `a call on the current channel stays on it`() {
        val connection = connection("only")
        connection.channel().call().start()
        channels.single().calls.assert().isEqualTo(1)
    }

    @Test
    fun `a call cancelled before it starts is cancelled as it starts`() {
        val connection = connection("only")
        val call = connection.channel().call()
        call.cancel("No longer wanted.", null)
        channels.single().cancelled.assert().isEqualTo(0)
        call.start()
        channels.single().cancelled.assert().isEqualTo(1)
        call.cancel("Again.", null)
        channels.single().cancelled.assert().isEqualTo(2)
    }

    @Test
    fun `the next channel is built outside the lock, as a factory may block`() {
        val connection = connection("first", "second")
        val failed = connection.channel()
        var seen: Channel? = null
        building = {
            seen = CompletableFuture.supplyAsync { connection.channel() }.get(2, TimeUnit.SECONDS)
        }
        connection.rotate(failed)
        seen.assert().isSameAs(failed)
        connection.channel().assert().isNotSameAs(failed)
    }

    @Test
    fun `a failure noticed again while the next channel is built opens no other`() {
        val connection = connection("only")
        val failed = connection.channel()
        // The keep-alive notices as well, while the failure of the watch stream is being handled.
        building = {
            building = {}
            connection.rotate(failed)
        }
        connection.rotate(failed)
        channels.size.assert().isEqualTo(2)
        channels.first().isShutdown.assert().isTrue()
    }

    @Test
    fun `closing while the next channel is built shuts that one down too`() {
        val connection = connection("only")
        val failed = connection.channel()
        building = { connection.close() }
        connection.rotate(failed)
        channels.map { it.isShutdown }.assert().containsExactly(true, true)
    }

    @Test
    fun `a channel that cannot be built leaves the current one, and the next failure tries the one after`() {
        val connection = connection("first", "second", "third")
        val failed = connection.channel()
        building = {
            building = {}
            throw UncheckedIOException(IOException("/etc/cosky/ca.pem cannot be read."))
        }
        connection.rotate(failed)
        connection.channel().assert().isSameAs(failed)
        channels.single().isShutdown.assert().isFalse()

        connection.rotate(failed)
        channels.map { it.endpoint }.assert().containsExactly("first", "third")
        connection.channel().assert().isNotSameAs(failed)
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

    @Test
    fun `failures noticed while closing open no channel that would stay open`() {
        val connection = connection("first", "second")
        val failed = connection.channel()
        // The watch stream and the keep-alive both notice, after closing has taken the channel and before it is done.
        channels.single().onShutdown = { repeat(2) { connection.rotate(failed) } }
        connection.close()
        channels.size.assert().isEqualTo(1)
        channels.single().isShutdown.assert().isTrue()
    }

    @Test
    fun `closing while a failure moves on shuts down the channel it moved on to`() {
        val connection = connection("first", "second")
        val failed = connection.channel()
        channels.single().onShutdown = { connection.close() }
        connection.rotate(failed)
        channels.map { it.isShutdown }.assert().containsExactly(true, true)
    }

    @Test
    fun `closing again changes nothing, and neither does a failure after it`() {
        val connection = connection("first", "second")
        val channel = connection.channel()
        connection.close()
        connection.close()
        connection.rotate(channel)
        channels.size.assert().isEqualTo(1)
        channels.single().isShutdown.assert().isTrue()
    }
}
