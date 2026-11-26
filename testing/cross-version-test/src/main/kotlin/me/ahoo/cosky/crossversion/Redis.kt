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

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.utility.DockerImageName

/**
 * The Redis that servers of both versions share: a server in a container reaches it on [network], one on this machine
 * at its mapped port.
 */
class Redis : AutoCloseable {
    val network: Network = Network.newNetwork()

    private val container = GenericContainer(DockerImageName.parse(IMAGE))
        .withNetwork(network)
        .withNetworkAliases(ALIAS)
        .withExposedPorts(PORT)
        .also { it.start() }

    /** For a server in a container on [network]. */
    val uriInNetwork: String = "redis://$ALIAS:$PORT"

    /** For a server on this machine. */
    val uri: String = "redis://${container.host}:${container.getMappedPort(PORT)}"

    /**
     * Runs a script, as a test does to stand in for what only a failover can do.
     */
    fun eval(script: String, keys: List<String>, vararg arguments: String) {
        val command = listOf("redis-cli", "EVAL", script, keys.size.toString()) + keys + arguments
        val result = container.execInContainer(*command.toTypedArray())
        val failed = result.exitCode != 0 || result.stdout.startsWith("ERR") || result.stdout.startsWith("(error)")
        check(!failed) { "The script failed: ${result.stdout}${result.stderr}" }
    }

    override fun close() {
        container.stop()
        network.close()
    }

    private companion object {
        /** As the other modules' tests run on, pinned by its tag. */
        const val IMAGE = "redis:7.4-alpine"
        const val ALIAS = "redis"
        const val PORT = 6379
    }
}
