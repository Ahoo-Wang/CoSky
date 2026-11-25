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
import kotlin.time.toKotlinDuration

/**
 * Server settings (design §7.6). A duration without a unit, as an environment variable may well give one, is in
 * seconds: `COSKY_LEASE_REAPER_GRACE=30` is 30 seconds. `META-INF/spring-configuration-metadata.json` describes each
 * property for IDEs, as `wiki/guide/properties.md` does for people.
 *
 * A value that cannot work, such as a reaper interval of zero, stops the start as a [StartupFailure] that names the
 * property and the value. Only values are checked: a `cosky.*` key the server does not know is ignored, as Kubernetes
 * gives every pod `COSKY_SERVICE_HOST`, `COSKY_PORT` and the like for a Service named `cosky`.
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
    ) {
        init {
            requirePositive("cosky.view.topology-flush-interval", topologyFlushInterval, "30s")
        }
    }

    /**
     * @param enabled off only for development: everyone may then do everything, which the server says at start.
     * @param bootstrapToken becomes the admin ServiceAccount while no RoleBinding exists: on the first start, and when
     * the store lost its data (design §6.3). Its secret takes at least 22 base62 characters.
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
    data class Store(val type: String = REDIS, val redis: Redis = Redis()) {
        init {
            requireSetting(type == REDIS || type == MEMORY, "cosky.store.type", "'$type'", "$REDIS or $MEMORY", REDIS)
        }
    }

    data class Redis(
        val uri: String = "redis://localhost:6379",
        val cluster: Boolean = false,
        @param:DurationUnit(ChronoUnit.SECONDS) val tailPollInterval: Duration = Duration.ofSeconds(1),
    ) {
        init {
            requirePositive("cosky.store.redis.tail-poll-interval", tailPollInterval, "1s")
        }

        /**
         * Leaves out the user and password a URI may hold.
         */
        override fun toString(): String = "Redis(uri=${uri.replace(USER_INFO, "//$REDACTED@")}, cluster=$cluster, " +
            "tailPollInterval=$tailPollInterval)"
    }

    /**
     * @param minRevisionTimeout how long a read waits for a view at all, so zero would fail every read.
     */
    data class Read(
        @param:DurationUnit(ChronoUnit.SECONDS) val minRevisionTimeout: Duration = Duration.ofSeconds(3),
    ) {
        init {
            requirePositive("cosky.read.min-revision-timeout", minRevisionTimeout, "3s")
        }
    }

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
    ) {
        init {
            // The newest revision is kept however large, so any window works; one of nothing does not.
            requireSetting(window >= 1, "cosky.watch.window", window, "at least 1", "10000")
            requireSetting(windowBytes.toBytes() > 0, "cosky.watch.window-bytes", windowBytes, "larger than 0", "64MB")
            requirePositive("cosky.watch.progress-interval", progressInterval, "10s")
            requireSetting(maxPerStream >= 1, "cosky.watch.max-per-stream", maxPerStream, "at least 1", "1000")
            requirePositive("cosky.watch.anti-entropy-interval", antiEntropyInterval, "5m")
        }
    }

    /**
     * Zero is a value that works for [reaperGrace] and [protectionMaxDuration], turning off what they protect with, as
     * a threshold of 1 does for mass expiry; [protectionMinLeases] of 0 leaves no namespace too small for it.
     *
     * @param reaperInterval a tick, or a call to the store, that takes longer than five of these counts as an outage,
     * which starts the grace again: zero would keep the reaper in its grace for good.
     * @param lockTtl outlasts [reaperInterval], or the lock lapses between two renewals and leadership changes hands.
     */
    data class Lease(
        @param:DurationUnit(ChronoUnit.SECONDS) val reaperInterval: Duration = Duration.ofSeconds(1),
        @param:DurationUnit(ChronoUnit.SECONDS) val reaperGrace: Duration = Duration.ofSeconds(30),
        val reaperMaxPerTick: Int = 100,
        @param:DurationUnit(ChronoUnit.SECONDS) val lockTtl: Duration = Duration.ofSeconds(15),
        val protectionThreshold: Double = 0.5,
        val protectionMinLeases: Int = 10,
        @param:DurationUnit(ChronoUnit.SECONDS) val protectionMaxDuration: Duration = Duration.ofMinutes(5),
    ) {
        init {
            requirePositive("cosky.lease.reaper-interval", reaperInterval, "1s")
            requireNotNegative("cosky.lease.reaper-grace", reaperGrace, "30s")
            requireSetting(
                reaperMaxPerTick >= 1,
                "cosky.lease.reaper-max-per-tick",
                reaperMaxPerTick,
                "at least 1",
                "100",
            )
            requireSetting(
                lockTtl > reaperInterval,
                "cosky.lease.lock-ttl",
                textOf(lockTtl),
                "longer than cosky.lease.reaper-interval, ${textOf(reaperInterval)}",
                "15s",
                why = "or the lock lapses between two renewals",
            )
            requireSetting(
                protectionThreshold in 0.0..1.0,
                "cosky.lease.protection-threshold",
                protectionThreshold,
                "between 0 and 1",
                "0.5",
            )
            requireSetting(
                protectionMinLeases >= 0,
                "cosky.lease.protection-min-leases",
                protectionMinLeases,
                "0 or more",
                "10",
            )
            requireNotNegative("cosky.lease.protection-max-duration", protectionMaxDuration, "5m")
        }
    }

    companion object {
        const val REDIS = "redis"
        const val MEMORY = "memory"
        private const val REDACTED = "****"
        private val USER_INFO = Regex("//[^/@]*@")
    }
}

/**
 * Stops the start unless [valid], saying what [property], set to [value], must be instead, and its default.
 *
 * @param why what a value that is not [must] does, where that is not plain.
 */
private fun requireSetting(
    valid: Boolean,
    property: String,
    value: Any,
    must: String,
    default: String,
    why: String? = null,
) {
    if (!valid) {
        val variable = property.uppercase().replace('.', '_').replace('-', '_')
        throw StartupFailure(
            "$property is $value: it must be $must${why?.let { ", $it" }.orEmpty()}.",
            "Set $property ($variable) to a value that is $must, or leave it out for its default of $default.",
        )
    }
}

private fun requirePositive(property: String, value: Duration, default: String) {
    requireSetting(value.isPositive, property, textOf(value), "longer than 0", default)
}

private fun requireNotNegative(property: String, value: Duration, default: String) {
    requireSetting(!value.isNegative, property, textOf(value), "0 or longer", default)
}

/** As in a message: `500ms`, `5m`. */
private fun textOf(duration: Duration): String = duration.toKotlinDuration().toString()
