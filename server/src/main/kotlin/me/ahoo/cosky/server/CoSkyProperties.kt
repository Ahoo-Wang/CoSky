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
import org.springframework.boot.convert.DurationUnit
import org.springframework.util.unit.DataSize
import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * Server settings (design §7.6). A duration without a unit, as an environment variable may well give one, is in
 * seconds: `COSKY_LEASE_REAPER_GRACE=30` is 30 seconds. `META-INF/spring-configuration-metadata.json` describes each
 * property for IDEs, as `wiki/guide/properties.md` does for people.
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
    data class View(
        @param:DurationUnit(ChronoUnit.SECONDS) val topologyFlushInterval: Duration = Duration.ofSeconds(30),
    )

    /**
     * @param enabled off only for development: everyone may then do everything.
     * @param bootstrapToken becomes the admin ServiceAccount on the first start (design §6.3).
     * @param oidc lets people sign in, when its issuer is set.
     * @param namespaceAccounts gives every namespace a ServiceAccount of its own name, bound to `client` there, whose
     * token its applications share (design D45).
     */
    data class Auth(
        val enabled: Boolean = true,
        val bootstrapToken: String? = null,
        val oidc: Oidc = Oidc(),
        val namespaceAccounts: Boolean = true,
    ) {
        /**
         * Leaves the token out, should these settings ever be logged or put in a message.
         */
        override fun toString(): String =
            "Auth(enabled=$enabled, bootstrapToken=${bootstrapToken?.let { REDACTED }}, oidc=$oidc, " +
                "namespaceAccounts=$namespaceAccounts)"
    }

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
        @param:DurationUnit(ChronoUnit.SECONDS) val tailPollInterval: Duration = Duration.ofSeconds(1),
    ) {
        /**
         * Leaves out the user and password a URI may hold.
         */
        override fun toString(): String = "Redis(uri=${uri.replace(USER_INFO, "//$REDACTED@")}, cluster=$cluster, " +
            "tailPollInterval=$tailPollInterval)"
    }

    data class Read(
        @param:DurationUnit(ChronoUnit.SECONDS) val minRevisionTimeout: Duration = Duration.ofSeconds(3),
    )

    /**
     * @param window changes kept in memory for resuming, per namespace, and in the store's log.
     * @param windowBytes the same in bytes: large Configs reach this long before [window] (design D36, D38).
     */
    data class Watch(
        val window: Int = 10_000,
        val windowBytes: DataSize = DataSize.ofMegabytes(64),
        @param:DurationUnit(ChronoUnit.SECONDS) val progressInterval: Duration = Duration.ofSeconds(10),
        val maxPerStream: Int = 1000,
        @param:DurationUnit(ChronoUnit.SECONDS) val antiEntropyInterval: Duration = Duration.ofMinutes(5),
    )

    data class Lease(
        @param:DurationUnit(ChronoUnit.SECONDS) val reaperInterval: Duration = Duration.ofSeconds(1),
        @param:DurationUnit(ChronoUnit.SECONDS) val reaperGrace: Duration = Duration.ofSeconds(30),
        val reaperMaxPerTick: Int = 100,
        @param:DurationUnit(ChronoUnit.SECONDS) val lockTtl: Duration = Duration.ofSeconds(15),
        val protectionThreshold: Double = 0.5,
        val protectionMinLeases: Int = 10,
        @param:DurationUnit(ChronoUnit.SECONDS) val protectionMaxDuration: Duration = Duration.ofMinutes(5),
    )

    companion object {
        const val REDIS = "redis"
        const val MEMORY = "memory"
        private const val REDACTED = "****"
        private val USER_INFO = Regex("//[^/@]*@")
    }
}
