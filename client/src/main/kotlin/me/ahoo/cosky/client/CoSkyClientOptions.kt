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
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * @param endpoints servers to try in turn, each `host:port`.
 * @param token sent as `Authorization: Bearer` when set.
 * @param clientName names this application in the server's topology view (design D30); only a label.
 * @param snapshotDir where views survive a restart (design §7.5); null turns snapshots off.
 * @param snapshotIncludeConfig false keeps Config contents off the disk, since snapshots are not encrypted.
 * @param resyncInterval how often a watch starts over from a snapshot, as a backstop for anything an incremental path
 * could miss (§6.4); ±20% jitter is added.
 * @param leaseTtl TTL of the lease; renewed every third of it.
 * @param closeTimeout how long closing waits to revoke leases, after which the server expires them.
 * @param callTimeout how long a call waits for its answer. Streams have none.
 * @param keepAliveTime how often an open connection is pinged, which is what notices a server that is silently gone. A
 * server ends the connections of clients pinging more often than `spring.grpc.server.keepalive.permit.time`.
 * @param keepAliveTimeout how long a ping waits for its answer before the connection counts as dead.
 * @param tls connects over TLS. Without it the token crosses the network in the clear.
 * @param tlsTrustedCertificates a PEM file of the certificates to trust, for a private CA. The JVM's trust store
 * decides otherwise.
 */
@Suppress("MagicNumber") // The defaults of design §7.5.
data class CoSkyClientOptions(
    val endpoints: List<String>,
    val token: String? = null,
    val clientName: String? = null,
    val snapshotDir: Path? = Path.of(System.getProperty("user.home"), ".cosky"),
    val snapshotIncludeConfig: Boolean = true,
    val snapshotDebounce: Duration = 1.seconds,
    val resyncInterval: Duration = 30.minutes,
    val reconnectMin: Duration = 100.milliseconds,
    val reconnectMax: Duration = 10.seconds,
    val reRegisterJitter: Duration = 5.seconds,
    val leaseTtl: Duration = 30.seconds,
    val closeTimeout: Duration = 3.seconds,
    val callTimeout: Duration = 10.seconds,
    val keepAliveTime: Duration = 15.seconds,
    val keepAliveTimeout: Duration = 5.seconds,
    val tls: Boolean = false,
    val tlsTrustedCertificates: Path? = null,
    val channelFactory: (String) -> ManagedChannel = { endpoint ->
        val channel = if (tls) {
            val credentials = TlsChannelCredentials.newBuilder()
            tlsTrustedCertificates?.let { credentials.trustManager(it.toFile()) }
            Grpc.newChannelBuilder(endpoint, credentials.build())
        } else {
            ManagedChannelBuilder.forTarget(endpoint).usePlaintext()
        }
        channel
            .keepAliveTime(keepAliveTime.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .keepAliveTimeout(keepAliveTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .build()
    },
) {
    init {
        require(endpoints.isNotEmpty()) { "At least one endpoint is required." }
    }

    /**
     * Leaves the token out, since options end up in messages and logs.
     */
    override fun toString(): String = "CoSkyClientOptions(endpoints=$endpoints, clientName=$clientName, tls=$tls)"
}
