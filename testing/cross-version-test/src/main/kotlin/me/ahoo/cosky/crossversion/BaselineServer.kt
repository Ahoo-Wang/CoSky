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

import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.time.Duration

/**
 * The release's server, from its image. A restart stops and starts the same container, on the same ports.
 *
 * @param bootstrapToken turns auth on, the default, and becomes the admin should no RoleBinding exist yet.
 * @param reaperGrace how long a new reaper leader waits before reaping, during which expired leases can be renewed.
 */
class BaselineServer(
    redis: Redis,
    bootstrapToken: String,
    reaperGrace: Duration = REAPER_GRACE,
) : CoSkyServer("baseline") {
    private val container: GenericContainer<*> = GenericContainer(DockerImageName.parse(IMAGE))
        .withNetwork(redis.network)
        .withExposedPorts(HTTP_PORT, GRPC_PORT)
        .withEnv("COSKY_BOOTSTRAP_TOKEN", bootstrapToken)
        .withCommand(*arguments(redis.uriInNetwork, reaperGrace).toTypedArray())
        .waitingFor(Wait.forHttp(READINESS).forPort(HTTP_PORT).withStartupTimeout(STARTUP_TIMEOUT))
        .also { it.portBindings = listOf("$httpPort:$HTTP_PORT", "$grpcPort:$GRPC_PORT") }

    override val host: String
        get() = container.host

    override val running: Boolean
        get() = container.containerId != null && container.isRunning

    override fun start() {
        val id = container.containerId
        if (id == null) {
            container.start()
            return
        }
        DockerClientFactory.instance().client().startContainerCmd(id).exec()
        awaitReady { container.isRunning }
    }

    override fun stop() {
        DockerClientFactory.instance().client().stopContainerCmd(container.containerId)
            .withTimeout(STOP_SECONDS)
            .exec()
        keepLogs()
    }

    override fun close() {
        if (container.containerId != null) {
            keepLogs()
        }
        container.stop()
    }

    /** All it logged since it was created, restarts included. */
    private fun keepLogs() {
        File(logs(), "baseline.log").writeText(container.logs)
    }

    companion object {
        /** The release's version, from the build. */
        val VERSION: String = requiredProperty("cosky.baseline.version")
        val IMAGE = "ahoowang/cosky:$VERSION"
        private const val HTTP_PORT = 8080
        private const val GRPC_PORT = 9090
        private const val STOP_SECONDS = 30
    }
}
