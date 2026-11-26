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

package me.ahoo.cosky.examples.e2e

import java.io.File
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * The server and the examples, each a process of its own on its real runtime classpath, and what a test needs to call
 * them and wait for them.
 */
internal class Processes : AutoCloseable {
    private val http = HttpClient.newHttpClient()
    private val started = mutableListOf<Process>()
    private val logs = File(System.getProperty("cosky.logs")).apply { mkdirs() }

    /**
     * @param runnable which classpath: `server`, `provider` or `consumer`.
     * @param log the name of its log file, unique within the run.
     * @param environment set on top of the test's own, from which the variables of CoSky are left out: a token in the
     * developer's shell must not stand in for the one a test gives, or for none.
     */
    fun start(
        runnable: String,
        log: String,
        mainClass: String,
        arguments: List<String>,
        environment: Map<String, String> = emptyMap(),
    ): Process {
        val java = File(System.getProperty("java.home"), "bin/java").path
        // They call each other on this machine. Spring Boot 4.2 has WebClient follow the proxy system properties, which
        // the JDK on macOS sets from the system's proxy, whose exceptions it may name in a form Reactor Netty does not
        // read: that proxy would carry the calls elsewhere.
        val command = listOf(
            java,
            "-Dhttp.nonProxyHosts=*",
            "-cp",
            System.getProperty("cosky.classpath.$runnable"),
            mainClass,
        ) + arguments
        return ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(logOf(log))
            .apply {
                environment().keys.removeIf { it.startsWith(COSKY_VARIABLES) }
                environment().putAll(environment)
            }
            .start()
            .also { started += it }
    }

    fun logOf(log: String): File = File(logs, "$log.log")

    override fun close() {
        started.forEach { it.destroy() }
        started.forEach {
            if (!it.waitFor(TERMINATION_SECONDS, TimeUnit.SECONDS)) {
                it.destroyForcibly()
            }
        }
    }

    fun get(url: String, token: String? = null): HttpResponse<String> = send(request(url, token).GET())

    fun put(url: String, body: String, token: String? = null): HttpResponse<String> =
        send(request(url, token).header("Content-Type", "application/json").PUT(BodyPublishers.ofString(body)))

    fun post(url: String, body: String, token: String? = null): HttpResponse<String> =
        send(request(url, token).header("Content-Type", "application/json").POST(BodyPublishers.ofString(body)))

    private fun request(url: String, token: String?): HttpRequest.Builder =
        HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(REQUEST_SECONDS)).apply {
            token?.let { header("Authorization", "Bearer $it") }
        }

    private fun send(request: HttpRequest.Builder): HttpResponse<String> =
        http.send(request.build(), BodyHandlers.ofString())

    fun eventually(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (!runCatching(condition).getOrDefault(false)) {
            check(System.nanoTime() < deadline) { "Timed out waiting for $what. The logs are in $logs." }
            Thread.sleep(POLL_MILLIS)
        }
    }

    companion object {
        const val SERVER = "me.ahoo.cosky.server.CoSkyServerApplicationKt"
        const val PROVIDER = "me.ahoo.cosky.examples.provider.ProviderApplicationKt"
        const val CONSUMER = "me.ahoo.cosky.examples.consumer.ConsumerApplicationKt"
        const val HTTP_OK = 200
        const val TERMINATION_SECONDS = 15L
        const val WAIT_SECONDS = 60L
        private const val COSKY_VARIABLES = "COSKY_"
        private const val REQUEST_SECONDS = 5L
        private const val POLL_MILLIS = 200L

        fun freePort(): Int = ServerSocket(0).use { it.localPort }
    }
}
