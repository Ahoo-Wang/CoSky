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

import io.grpc.Grpc
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.TlsChannelCredentials
import me.ahoo.cosky.api.validation.Limits
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The client's settings, made with [CoSkyClientOptions]:
 *
 * ```kotlin
 * CoSkyClientOptions(listOf("cosky-0.internal:9090")) {
 *     token = System.getenv("COSKY_TOKEN")
 *     clientName = "order-service"
 * }
 * ```
 *
 * A class with a [Builder] rather than a data class, so that a setting added in a later 8.x keeps applications built
 * against an earlier one working. [Builder] documents each setting.
 */
class CoSkyClientOptions private constructor(builder: Builder) {
    val endpoints: List<String> = builder.endpoints.toList()
    val token: String? = builder.token
    val clientName: String? = builder.clientName
    val snapshotDir: Path? = builder.snapshotDir
    val snapshotIncludeConfig: Boolean = builder.snapshotIncludeConfig
    val snapshotDebounce: Duration = builder.snapshotDebounce
    val resyncInterval: Duration = builder.resyncInterval
    val reconnectMin: Duration = builder.reconnectMin
    val reconnectMax: Duration = builder.reconnectMax
    val reRegisterJitter: Duration = builder.reRegisterJitter
    val leaseTtl: Duration = builder.leaseTtl
    val closeTimeout: Duration = builder.closeTimeout
    val callTimeout: Duration = builder.callTimeout
    val keepAliveTime: Duration = builder.keepAliveTime
    val keepAliveTimeout: Duration = builder.keepAliveTimeout
    val tls: Boolean = builder.tls
    val tlsTrustedCertificates: Path? = builder.tlsTrustedCertificates
    val channelFactory: (String) -> ManagedChannel = builder.channelFactory ?: ::defaultChannel

    init {
        require(endpoints.isNotEmpty()) { "At least one endpoint is required." }
        // A lease lasts whole seconds within the protocol's range. Anything else would reach the server as 0, which
        // asks for its default, while renewals still followed this value.
        val ttlRange = Limits.DEFAULT.minLeaseTtlSeconds..Limits.DEFAULT.maxLeaseTtlSeconds
        require(leaseTtl.inWholeSeconds.seconds == leaseTtl && leaseTtl.inWholeSeconds in ttlRange) {
            "leaseTtl must be whole seconds within $ttlRange, not $leaseTtl."
        }
    }

    private fun defaultChannel(endpoint: String): ManagedChannel {
        val channel = if (tls) {
            val credentials = TlsChannelCredentials.newBuilder()
            tlsTrustedCertificates?.let { credentials.trustManager(it.toFile()) }
            Grpc.newChannelBuilder(endpoint, credentials.build())
        } else {
            ManagedChannelBuilder.forTarget(endpoint).usePlaintext()
        }
        return channel
            .keepAliveTime(keepAliveTime.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .keepAliveTimeout(keepAliveTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .build()
    }

    /**
     * Leaves the token out, since options end up in messages and logs.
     */
    override fun toString(): String = "CoSkyClientOptions(endpoints=$endpoints, clientName=$clientName, tls=$tls)"

    /**
     * @param endpoints servers to try in turn, each `host:port`.
     */
    @Suppress("MagicNumber") // The defaults of design §7.5.
    class Builder(internal val endpoints: List<String>) {
        /** Sent as `Authorization: Bearer` when set. */
        var token: String? = null

        /** Names this application in the server's topology view (design D30); only a label. */
        var clientName: String? = null

        /** Where views survive a restart (design §7.5); null turns snapshots off. */
        var snapshotDir: Path? = Path.of(System.getProperty("user.home"), ".cosky")

        /** False keeps Config contents off the disk, since snapshots are not encrypted. */
        var snapshotIncludeConfig: Boolean = true

        /** How long changes gather before a snapshot is written. */
        var snapshotDebounce: Duration = 1.seconds

        /**
         * How often a watch starts over from a snapshot, as a backstop for anything an incremental path could miss
         * (§6.4); ±20% jitter is added.
         */
        var resyncInterval: Duration = 30.minutes

        /** The first wait before reconnecting; it doubles, with jitter, up to [reconnectMax]. */
        var reconnectMin: Duration = 100.milliseconds

        var reconnectMax: Duration = 10.seconds

        /**
         * The most a client waits, at random, to put its instances back after losing them, so a fleet does not come at
         * once.
         */
        var reRegisterJitter: Duration = 5.seconds

        /** TTL of the lease, in whole seconds from 5 to 300; renewed every third of it. */
        var leaseTtl: Duration = 30.seconds

        /** How long closing waits to revoke leases, after which the server expires them. */
        var closeTimeout: Duration = 3.seconds

        /** How long a call waits for its answer. Streams have none. */
        var callTimeout: Duration = 10.seconds

        /**
         * How often an open connection is pinged, which is what notices a server that is silently gone. A server ends
         * the connections of clients pinging more often than `spring.grpc.server.keepalive.permit.time`.
         */
        var keepAliveTime: Duration = 15.seconds

        /** How long a ping waits for its answer before the connection counts as dead. */
        var keepAliveTimeout: Duration = 5.seconds

        /** Connects over TLS. Without it the token crosses the network in the clear. */
        var tls: Boolean = false

        /** A PEM file of the certificates to trust, for a private CA. The JVM's trust store decides otherwise. */
        var tlsTrustedCertificates: Path? = null

        /** Builds the gRPC channel to an endpoint instead, for anything the settings above do not cover. */
        var channelFactory: ((String) -> ManagedChannel)? = null

        fun build(): CoSkyClientOptions = CoSkyClientOptions(this)
    }
}

/**
 * @param endpoints servers to try in turn, each `host:port`.
 * @param settings sets anything else, as [CoSkyClientOptions.Builder] lists.
 */
fun CoSkyClientOptions(
    endpoints: List<String>,
    settings: CoSkyClientOptions.Builder.() -> Unit = {},
): CoSkyClientOptions = CoSkyClientOptions.Builder(endpoints).apply(settings).build()
