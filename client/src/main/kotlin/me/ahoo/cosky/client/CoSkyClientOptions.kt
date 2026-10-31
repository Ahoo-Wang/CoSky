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

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * @param endpoints servers to try in turn, each `host:port`.
 * @param token sent as `Authorization: Bearer` when set.
 * @param snapshotDir where views survive a restart (design §7.5); null turns snapshots off.
 * @param snapshotIncludeConfig false keeps Config contents off the disk, since snapshots are not encrypted.
 * @param resyncInterval how often a watch starts over from a snapshot, as a backstop for anything an incremental path
 * could miss (§6.4); ±20% jitter is added.
 * @param leaseTtl TTL of the lease; renewed every third of it.
 * @param closeTimeout how long closing waits to revoke leases, after which the server expires them.
 */
@Suppress("MagicNumber") // The defaults of design §7.5.
data class CoSkyClientOptions(
    val endpoints: List<String>,
    val token: String? = null,
    val snapshotDir: Path? = Path.of(System.getProperty("user.home"), ".cosky"),
    val snapshotIncludeConfig: Boolean = true,
    val snapshotDebounce: Duration = 1.seconds,
    val resyncInterval: Duration = 30.minutes,
    val reconnectMin: Duration = 100.milliseconds,
    val reconnectMax: Duration = 10.seconds,
    val reRegisterJitter: Duration = 5.seconds,
    val leaseTtl: Duration = 30.seconds,
    val closeTimeout: Duration = 3.seconds,
    val channelFactory: (String) -> ManagedChannel = { endpoint ->
        ManagedChannelBuilder.forTarget(endpoint).usePlaintext().build()
    },
) {
    init {
        require(endpoints.isNotEmpty()) { "At least one endpoint is required." }
    }
}
