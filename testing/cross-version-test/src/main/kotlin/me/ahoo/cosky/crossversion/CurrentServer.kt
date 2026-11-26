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
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * This tree's server, a process of its own from `:server:installDist`, as the image runs it: the same start script and
 * JVM flags, on JDK 25. A restart starts a new process on the same ports.
 *
 * @param bootstrapToken turns auth on, the default, and becomes the admin should no RoleBinding exist yet.
 * @param reaperGrace how long a new reaper leader waits before reaping, during which expired leases can be renewed.
 */
class CurrentServer(
    private val redisUri: String,
    private val bootstrapToken: String,
    private val reaperGrace: Duration = REAPER_GRACE,
) : CoSkyServer("current") {
    private var process: Process? = null
    private var starts = 0

    override val host: String = "localhost"

    override val running: Boolean
        get() = process?.isAlive == true

    override fun start() {
        check(process == null) { "The current server runs already." }
        val home = File(requiredProperty("cosky.current.home"))
        val command = listOf(File(home, "bin/cosky-server").path, "--server.port=$httpPort") +
            "--spring.grpc.server.port=$grpcPort" + arguments(redisUri, reaperGrace)
        val builder = ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(File(logs(), "current-${++starts}.log"))
        builder.environment()["JAVA_HOME"] = requiredProperty("cosky.current.java-home")
        builder.environment()["COSKY_BOOTSTRAP_TOKEN"] = bootstrapToken
        val started = builder.start()
        process = started
        awaitReady { started.isAlive }
    }

    override fun stop() {
        val running = process ?: return
        // SIGTERM: the start script execs the JVM, which shuts down gracefully.
        running.destroy()
        if (!running.waitFor(STOP_SECONDS, TimeUnit.SECONDS)) {
            running.destroyForcibly().waitFor()
        }
        process = null
    }

    override fun close() {
        stop()
    }

    private companion object {
        const val STOP_SECONDS = 30L
    }
}
