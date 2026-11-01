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

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.unit.DataSize
import java.time.Duration

/**
 * Server settings (design §7.6).
 */
@Suppress("MagicNumber") // The defaults of design §7.6.
@ConfigurationProperties("cosky")
data class CoSkyProperties(
    val store: Store = Store(),
    val watch: Watch = Watch(),
    val lease: Lease = Lease(),
    val read: Read = Read(),
    val auth: Auth = Auth(),
    val view: View = View(),
) {
    data class View(val topologyFlushInterval: Duration = Duration.ofSeconds(30))

    /**
     * @param enabled off only for development: everyone may then do everything.
     * @param bootstrapToken becomes the admin ServiceAccount on the first start (design §6.3).
     * @param oidc lets people sign in, when its issuer is set.
     */
    data class Auth(val enabled: Boolean = true, val bootstrapToken: String? = null, val oidc: Oidc = Oidc())

    /**
     * @param clientId the Dashboard's client, which it signs people in with.
     * @param audience what a token must be for; the client when not set.
     * @param scopes what the Dashboard asks for. Some providers only send groups when asked to.
     */
    data class Oidc(
        val issuer: String? = null,
        val clientId: String? = null,
        val audience: String? = null,
        val groupsClaim: String = "groups",
        val scopes: List<String> = listOf("openid", "profile"),
    )

    /**
     * @param type `redis`, or `memory` for development: nothing survives the process.
     */
    data class Store(val type: String = REDIS, val redis: Redis = Redis())

    data class Redis(
        val uri: String = "redis://localhost:6379",
        val cluster: Boolean = false,
        val tailPollInterval: Duration = Duration.ofSeconds(1),
    )

    data class Read(val minRevisionTimeout: Duration = Duration.ofSeconds(3))

    /**
     * @param window changes kept in memory for resuming, per namespace, and in the store's log.
     * @param windowBytes the same in bytes, for the memory only: large Configs reach this long before [window] (D36).
     */
    data class Watch(
        val window: Int = 10_000,
        val windowBytes: DataSize = DataSize.ofMegabytes(64),
        val progressInterval: Duration = Duration.ofSeconds(10),
        val maxPerStream: Int = 1000,
        val antiEntropyInterval: Duration = Duration.ofMinutes(5),
    )

    data class Lease(
        val reaperInterval: Duration = Duration.ofSeconds(1),
        val reaperGrace: Duration = Duration.ofSeconds(30),
        val reaperMaxPerTick: Int = 100,
        val lockTtl: Duration = Duration.ofSeconds(15),
        val protectionThreshold: Double = 0.5,
        val protectionMinLeases: Int = 10,
        val protectionMaxDuration: Duration = Duration.ofMinutes(5),
    )

    companion object {
        const val REDIS = "redis"
        const val MEMORY = "memory"
    }
}
