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

package me.ahoo.cosky.migration

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.sync.RedisCommands
import me.ahoo.cosky.server.CoSkyServerApplication
import me.ahoo.cosky.server.auth.Tokens
import org.springframework.boot.builder.SpringApplicationBuilder
import org.testcontainers.containers.GenericContainer

/**
 * What the tests that run the whole tool share: a real Redis for 6.x to leave its keys in, and a real 8.0 server,
 * started when a test first needs it.
 */
internal object MigrationEnvironment {
    private const val REDIS_PORT = 6379
    private const val READY_ATTEMPTS = 100
    private const val READY_DELAY = 100L

    val TOKEN: String = Tokens.generate().plaintext

    private val CONTAINER: Redis = Redis().withExposedPorts(REDIS_PORT).also { it.start() }
    val REDIS_URI = "redis://${CONTAINER.host}:${CONTAINER.getMappedPort(REDIS_PORT)}"
    val REDIS: RedisCommands<String, String> = RedisClient.create(RedisURI.create(REDIS_URI)).connect().sync()

    /** The REST address of the 8.0 server. */
    val URL: String by lazy { startServer() }

    private fun startServer(): String {
        // Port 0: the system picks free ports as the server binds them, where one found free beforehand could be
        // taken by another process in between.
        val server = SpringApplicationBuilder(CoSkyServerApplication::class.java).run(
            "--server.port=0",
            "--spring.grpc.server.port=0",
            "--cosky.store.type=memory",
            "--cosky.auth.enabled=true",
            "--cosky.auth.bootstrap-token=$TOKEN",
        )
        val url = "http://localhost:${server.environment.getRequiredProperty("local.server.port")}"
        // A server just started refuses requests until its access rules have loaded.
        val target = RestTarget(url, TOKEN)
        repeat(READY_ATTEMPTS) {
            if (runCatching { target.namespaceExists("default") }.getOrDefault(false)) {
                return url
            }
            Thread.sleep(READY_DELAY)
        }
        error("The server did not become ready.")
    }

    private class Redis : GenericContainer<Redis>("redis:7.4-alpine")
}
