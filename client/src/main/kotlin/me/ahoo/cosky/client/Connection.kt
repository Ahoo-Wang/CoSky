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
import io.grpc.ClientInterceptor
import io.grpc.ClientInterceptors
import io.grpc.ForwardingClientCall
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.time.Duration

/**
 * The one channel that carries every watch and keep-alive (design §6.5). When a stream fails, whoever notices first
 * moves on to the next endpoint.
 */
internal class Connection(private val options: CoSkyClientOptions) : AutoCloseable {
    private val lock = Any()
    private var index = 0
    private var raw: ManagedChannel = options.channelFactory(options.endpoints[0])
    private var decorated: Channel = decorate(raw)

    fun channel(): Channel = synchronized(lock) { decorated }

    /**
     * Moves on from [failed], unless someone else already did.
     */
    fun rotate(failed: Channel) {
        val previous = synchronized(lock) {
            if (failed !== decorated || options.endpoints.size == 1) {
                return
            }
            val old = raw
            index = (index + 1) % options.endpoints.size
            raw = options.channelFactory(options.endpoints[index])
            decorated = decorate(raw)
            old
        }
        previous.shutdown()
    }

    override fun close() {
        val channel = synchronized(lock) { raw }
        channel.shutdown()
        if (!channel.awaitTermination(1, TimeUnit.SECONDS)) {
            channel.shutdownNow()
        }
    }

    private fun decorate(channel: ManagedChannel): Channel {
        val headers = Metadata()
        options.token?.let { headers.put(AUTHORIZATION, "Bearer $it") }
        options.clientName?.let { headers.put(CLIENT_NAME, it) }
        return ClientInterceptors.intercept(channel, ExtraHeaders(headers))
    }

    private class ExtraHeaders(private val extra: Metadata) : ClientInterceptor {
        override fun <Req, Resp> interceptCall(
            method: MethodDescriptor<Req, Resp>,
            callOptions: CallOptions,
            next: Channel,
        ): ClientCall<Req, Resp> =
            object : ForwardingClientCall.SimpleForwardingClientCall<Req, Resp>(next.newCall(method, callOptions)) {
                override fun start(responseListener: Listener<Resp>, headers: Metadata) {
                    headers.merge(extra)
                    super.start(responseListener, headers)
                }
            }
    }

    private companion object {
        val AUTHORIZATION: Metadata.Key<String> = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
        val CLIENT_NAME: Metadata.Key<String> = Metadata.Key.of("cosky-client-name", Metadata.ASCII_STRING_MARSHALLER)
    }
}

/**
 * Exponential backoff with jitter, restarted by [reset] after a success.
 */
internal class Backoff(private val min: Duration, private val max: Duration) {
    private var current = min

    fun next(): Duration {
        val jittered = current * (JITTER_FLOOR + Random.nextDouble() * JITTER_SPREAD)
        current = minOf(current * 2, max)
        return jittered
    }

    fun reset() {
        current = min
    }

    private companion object {
        const val JITTER_FLOOR = 0.5
        const val JITTER_SPREAD = 0.5
    }
}
