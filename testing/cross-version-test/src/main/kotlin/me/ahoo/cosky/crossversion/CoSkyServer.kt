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

package me.ahoo.cosky.crossversion

import java.io.File
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.SecureRandom
import java.time.Duration

/**
 * A CoSky server of one version or the other, on ports that stay the same across restarts, as a restarted replica's
 * clients come back to it.
 *
 * Both versions get the same settings, from the same arguments: reaping often, after a short grace, with a short reaper
 * lock, so that what takes a minute in production takes seconds here, and anti-entropy often, so that a server whose
 * view differed from what the other version wrote would count it.
 */
abstract class CoSkyServer(val name: String) : AutoCloseable {
    val httpPort: Int = freePort()
    val grpcPort: Int = freePort()

    abstract val host: String

    /** The gRPC endpoint, `host:port`. */
    val endpoint: String
        get() = "$host:$grpcPort"

    /** Where REST and Actuator answer. */
    val url: String
        get() = "http://$host:$httpPort"

    abstract val running: Boolean

    abstract fun start()

    /**
     * Stops it as Kubernetes does, with SIGTERM: it ends its streams and lets go of the reaper's lock.
     */
    abstract fun stop()

    fun restart() {
        stop()
        start()
    }

    fun get(path: String): HttpResponse<String> = HTTP.send(
        HttpRequest.newBuilder(URI("$url$path")).timeout(REQUEST_TIMEOUT).build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    /**
     * Whether this server leads the reaper: only the leader reports `cosky_lease_active`, one series per namespace.
     */
    fun leadsReaper(): Boolean = prometheus().any { it.startsWith("$LEASES_ACTIVE{") }

    /**
     * The sum of a metric's series on this server's Prometheus endpoint that have [labels], 0 when there are none.
     */
    fun metric(name: String, vararg labels: Pair<String, String>): Double {
        val wanted = labels.map { (label, value) -> "$label=\"$value\"" }
        return prometheus()
            .filter { line -> (line.startsWith("$name{") || line.startsWith("$name ")) && wanted.all { it in line } }
            .sumOf { it.substringAfterLast(' ').toDouble() }
    }

    private fun prometheus(): List<String> = get("/actuator/prometheus").also {
        check(it.statusCode() == HTTP_OK) { "$name answered ${it.statusCode()} for its metrics." }
    }.body().lines()

    /**
     * Waits for readiness, failing early once [alive] says the server stopped.
     */
    protected fun awaitReady(alive: () -> Boolean) {
        await("the $name server to be ready", STARTUP_TIMEOUT) {
            check(alive()) { "The $name server stopped. Its log is in ${logs()}." }
            runCatching { get(READINESS).statusCode() == HTTP_OK }.getOrDefault(false)
        }
    }

    companion object {
        const val READINESS = "/actuator/health/readiness"
        const val LEASES_ACTIVE = "cosky_lease_active"
        const val HTTP_OK = 200
        val STARTUP_TIMEOUT: Duration = Duration.ofMinutes(2)
        private val REQUEST_TIMEOUT = Duration.ofSeconds(5)
        private val HTTP: HttpClient = HttpClient.newHttpClient()
        private const val BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        private const val TOKEN_ID_LENGTH = 16
        private const val TOKEN_SECRET_LENGTH = 43
        private val random = SecureRandom()

        /**
         * Long enough for a client to come back once a restarted server is ready, and short enough that a lease it did
         * not renew then is reaped within seconds.
         */
        val REAPER_GRACE: Duration = Duration.ofSeconds(5)

        /**
         * The arguments both versions start with, beside those of their ports.
         *
         * @param reaperGrace how long a new reaper leader waits, during which expired leases can still be renewed.
         */
        fun arguments(redisUri: String, reaperGrace: Duration): List<String> = listOf(
            "--cosky.store.redis.uri=$redisUri",
            "--cosky.store.redis.tail-poll-interval=100ms",
            "--cosky.lease.reaper-interval=200ms",
            "--cosky.lease.reaper-grace=${reaperGrace.toMillis()}ms",
            "--cosky.lease.lock-ttl=3s",
            "--cosky.watch.anti-entropy-interval=2s",
        )

        /**
         * A token of the form servers take, for the bootstrap admin.
         */
        fun newToken(): String = "cosky_${base62(TOKEN_ID_LENGTH)}_${base62(TOKEN_SECRET_LENGTH)}"

        private fun base62(length: Int) = String(CharArray(length) { BASE62[random.nextInt(BASE62.length)] })

        /** Where the servers' logs go. */
        fun logs(): File = File(System.getProperty("cosky.logs") ?: "build/cross-version").apply { mkdirs() }

        fun requiredProperty(name: String): String =
            checkNotNull(System.getProperty(name)) { "System property $name is not set; run the tests with Gradle." }

        private fun freePort(): Int = ServerSocket(0).use { it.localPort }
    }
}

/**
 * Polls [condition] until it holds, for at most [timeout].
 */
fun await(what: String, timeout: Duration, condition: () -> Boolean) {
    val deadline = System.nanoTime() + timeout.toNanos()
    while (!condition()) {
        check(System.nanoTime() < deadline) { "Timed out after $timeout waiting for $what." }
        Thread.sleep(POLL_MILLIS)
    }
}

private const val POLL_MILLIS = 100L
