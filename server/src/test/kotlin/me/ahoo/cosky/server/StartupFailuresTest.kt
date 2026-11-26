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

import io.lettuce.core.RedisConnectionException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.auth.AuthBootstrap
import me.ahoo.cosky.server.auth.ParsedToken
import me.ahoo.cosky.server.auth.Tokens
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.BeanCreationException
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension

@ExtendWith(OutputCaptureExtension::class)
class StartupFailuresTest {
    @Test
    fun `a setting that cannot work stops the start, saying which and what it must be`(output: CapturedOutput) {
        val failure = runCatching {
            RunningServer(
                mapOf(
                    "cosky.store.type" to CoSkyProperties.MEMORY,
                    "cosky.auth.enabled" to "false",
                    "cosky.lease.reaper-interval" to "0s",
                ),
            ).close()
        }.exceptionOrNull()

        failure.assert().isNotNull()
        val report = output.out.substringAfter("APPLICATION FAILED TO START")
        report.assert().contains("cosky.lease.reaper-interval is 0s: it must be longer than 0.")
            .contains("Set cosky.lease.reaper-interval (COSKY_LEASE_REAPER_INTERVAL)")
    }

    @Test
    fun `a Redis out of reach at start says what to set, or how to do without`() {
        val failure = BeanCreationException("store", RedisConnectionException("Unable to connect to localhost:6379"))

        val analysis = RedisUnreachableFailureAnalyzer().analyze(failure)!!

        analysis.description.assert().contains("localhost:6379")
        analysis.action.assert().contains("COSKY_STORE_REDIS_URI").contains("--dev")
    }

    @Test
    fun `a Redis that would evict data stops the start, saying what to change on it`(output: CapturedOutput) {
        val redis = RedisContainer("redis-server", "--maxmemory-policy", "allkeys-lru").apply { start() }
        try {
            val uri = "redis://${redis.host}:${redis.getMappedPort(RedisContainer.REDIS_PORT)}"
            val failure = runCatching {
                RunningServer(mapOf("cosky.store.redis.uri" to uri, "cosky.auth.enabled" to "false")).close()
            }.exceptionOrNull()

            failure.assert().isNotNull()
            val report = output.out.substringAfter("APPLICATION FAILED TO START")
            report.assert().contains("This Redis cannot back CoSky: maxmemory_policy is allkeys-lru")
                .contains("with maxmemory-policy noeviction")
        } finally {
            redis.stop()
        }
    }

    @Test
    fun `a malformed bootstrap token stops the start, with one to use instead`() {
        val failure = runCatching { runBlocking { AuthBootstrap(MemoryStore()).run("mysecret") } }.exceptionOrNull()

        failure.assert().isInstanceOf(StartupFailure::class.java)
        val analysis = StartupFailureAnalyzer().analyze(BeanCreationException("bootstrap", failure!!))!!
        analysis.description.assert().contains("cosky_<id>_<secret>")
        analysis.action.assert().contains("COSKY_BOOTSTRAP_TOKEN").contains("openssl rand")
    }

    private fun bootstrapToken(secretLength: Int): String {
        val generated = Tokens.generate()
        return ParsedToken(generated.id, generated.secret.take(secretLength)).plaintext
    }

    @Test
    fun `a bootstrap token with a short secret stops the start, without showing it`() {
        val weak = bootstrapToken(AuthBootstrap.MIN_SECRET_LENGTH - 1)
        val failure = runCatching { runBlocking { AuthBootstrap(MemoryStore()).run(weak) } }.exceptionOrNull()

        failure.assert().isInstanceOf(StartupFailure::class.java)
        val analysis = StartupFailureAnalyzer().analyze(BeanCreationException("bootstrap", failure!!))!!
        analysis.description.assert().contains("at least 22 base62 characters")
        analysis.action.assert().contains("COSKY_BOOTSTRAP_TOKEN").contains("openssl rand")
        val secret = checkNotNull(Tokens.parse(weak)).secret
        listOf(analysis.description, analysis.action).forEach { it.assert().doesNotContain(secret) }
    }

    @Test
    fun `a weak bootstrap token stops every start, used or not, since a store that lost its data takes it`() =
        runBlocking<Unit> {
            val store = MemoryStore().apply { createNamespace(NamingRules.SYSTEM_NAMESPACE) }
            AuthBootstrap(store).run(bootstrapToken(AuthBootstrap.MIN_SECRET_LENGTH))
            store.readAll(NamingRules.SYSTEM_NAMESPACE).toList().filter { it.hasRoleBinding() }.assert().hasSize(1)

            listOf("cosky_abc_def", "mysecret").forEach { weak ->
                runCatching { AuthBootstrap(store).run(weak) }.exceptionOrNull()
                    .assert().isInstanceOf(StartupFailure::class.java)
            }
        }
}
