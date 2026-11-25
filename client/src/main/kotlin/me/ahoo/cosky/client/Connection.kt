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
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.time.Duration

/**
 * The one channel that carries every watch and keep-alive (design §6.5). When a stream fails, whoever notices first
 * moves on to a new channel, to the next endpoint.
 */
internal class Connection(private val options: CoSkyClientOptions) : AutoCloseable {
    private val lock = Any()
    private var index = 0
    private var raw: ManagedChannel = options.channelFactory(options.endpoints[0])

    /** Before the first channel, which [decorate] gives it. */
    private val tokens = TokenSource(options)

    private var decorated: Channel = decorate(raw)
    private var closed = false

    /** The channel being moved on from while the next one is built, so that a failure is handled once. */
    private var leaving: Channel? = null

    fun channel(): Channel = synchronized(lock) { decorated }

    /**
     * Moves on from [failed], unless someone else already did or is doing so, or the connection is closed. Cancelling
     * the client does not stop a failure that is already being handled, and a channel opened after closing took the
     * current one would never be shut down.
     *
     * With one endpoint, the next is a new channel to it. A replica that cannot serve, being behind or unable to read
     * its store, ends the whole stream for the client to go elsewhere (design D29); reconnecting over the same
     * connection would only reach it again, while a new one to a Kubernetes Service may reach another replica. A
     * channel's own backoff, up to two minutes, does not come into it either: the caller's decides (design §7.5).
     *
     * The channel is built outside the lock, as a factory may block, reading a PEM file or whatever an application's
     * does, and on JDK 21–23 blocking inside `synchronized` holds on to a carrier thread (design §7.5). One that cannot
     * be built leaves the current channel in place, until the next failure.
     */
    fun rotate(failed: Channel) {
        val next = synchronized(lock) {
            if (closed || failed !== decorated || leaving === failed) {
                return
            }
            leaving = failed
            (index + 1) % options.endpoints.size
        }
        val created = try {
            options.channelFactory(options.endpoints[next])
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            synchronized(lock) {
                leaving = null
                // The next failure tries the one after, rather than this one again and again.
                index = next
            }
            log.warn("Could not open a channel to {}; staying on the current one.", options.endpoints[next], e)
            return
        }
        val previous = synchronized(lock) {
            leaving = null
            if (closed) {
                created
            } else {
                index = next
                raw.also {
                    raw = created
                    decorated = decorate(created)
                }
            }
        }
        previous.shutdown()
    }

    override fun close() {
        val channel = synchronized(lock) {
            closed = true
            raw
        }
        channel.shutdown()
        if (!channel.awaitTermination(1, TimeUnit.SECONDS)) {
            channel.shutdownNow()
        }
    }

    private fun decorate(channel: ManagedChannel): Channel {
        val headers = Metadata()
        options.clientName?.let { headers.put(CLIENT_NAME, it) }
        return ClientInterceptors.intercept(Generation(channel), ExtraHeaders(headers, tokens))
    }

    /**
     * One channel of the connection. A call started on it once a rotation has shut it down goes to the channel that
     * took over: a caller may have taken it just before, for an attempt of several calls such as a registration, which
     * would otherwise fail for no fault of the server's. Calls under way finish where they are.
     */
    private inner class Generation(private val own: ManagedChannel) : Channel() {
        override fun <Req, Resp> newCall(
            method: MethodDescriptor<Req, Resp>,
            callOptions: CallOptions,
        ): ClientCall<Req, Resp> {
            val target = if (own.isShutdown) synchronized(lock) { raw } else own
            return target.newCall(method, callOptions)
        }

        override fun authority(): String = own.authority()
    }

    /**
     * The token is taken as each call starts, so that a rotated one goes out from then on.
     */
    private class ExtraHeaders(private val extra: Metadata, private val tokens: TokenSource) : ClientInterceptor {
        override fun <Req, Resp> interceptCall(
            method: MethodDescriptor<Req, Resp>,
            callOptions: CallOptions,
            next: Channel,
        ): ClientCall<Req, Resp> =
            object : ForwardingClientCall.SimpleForwardingClientCall<Req, Resp>(next.newCall(method, callOptions)) {
                override fun start(responseListener: Listener<Resp>, headers: Metadata) {
                    headers.merge(extra)
                    tokens.current()?.let { headers.put(AUTHORIZATION, "Bearer $it") }
                    super.start(responseListener, headers)
                }
            }
    }

    private companion object {
        val AUTHORIZATION: Metadata.Key<String> = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
        val CLIENT_NAME: Metadata.Key<String> = Metadata.Key.of("cosky-client-name", Metadata.ASCII_STRING_MARSHALLER)
        val log = LoggerFactory.getLogger(Connection::class.java)
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
