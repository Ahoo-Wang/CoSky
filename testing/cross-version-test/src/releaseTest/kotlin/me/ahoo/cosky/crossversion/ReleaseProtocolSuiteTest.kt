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

import io.grpc.Channel
import kotlinx.coroutines.runInterruptible
import me.ahoo.cosky.client.CoSkyClient
import me.ahoo.cosky.test.conformance.ProtocolConformanceTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach

/**
 * What a client of the release relies on from a server, as the release's own protocol suite says it, against this
 * tree's server on Redis, with auth on: an application on the release, talking to a server that was upgraded.
 */
class ReleaseProtocolSuiteTest : ProtocolConformanceTest() {
    private val clients = mutableListOf<CoSkyClient>()

    override val channel: Channel
        get() = calls.grpc

    override fun newClient(): CoSkyClient = client(ADMIN, server).also { clients += it }

    override suspend fun restartServer() {
        runInterruptible { server.restart() }
    }

    /**
     * Rewinds the namespace's timeline in place, as a failover to a replica that missed the last writes does.
     */
    override suspend fun loseWritesAfter(namespace: String, revision: Long) {
        val script = """
            local kept = redis.call('XRANGE', KEYS[2], '-', ARGV[1] .. '-9999')
            redis.call('DEL', KEYS[2])
            for _, entry in ipairs(kept) do
              redis.call('XADD', KEYS[2], entry[1], unpack(entry[2]))
            end
            redis.call('SET', KEYS[1], ARGV[1])
            return 1
        """.trimIndent()
        runInterruptible {
            REDIS.eval(script, listOf("cosky:{$namespace}:rev", "cosky:{$namespace}:log"), revision.toString())
        }
    }

    @AfterEach
    fun closeClients() {
        clients.forEach { it.close() }
    }

    companion object {
        private val ADMIN = CoSkyServer.newToken()
        private val REDIS = Redis()
        private val server = CurrentServer(REDIS.uri, ADMIN).also { it.start() }
        private val calls = Calls(server, ADMIN)

        @AfterAll
        @JvmStatic
        fun stop() {
            calls.close()
            server.close()
            REDIS.close()
        }
    }
}
