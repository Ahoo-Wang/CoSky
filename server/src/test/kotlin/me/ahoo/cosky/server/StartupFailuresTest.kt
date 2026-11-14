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
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.server.auth.AuthBootstrap
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.BeanCreationException

class StartupFailuresTest {
    @Test
    fun `a Redis out of reach at start says what to set, or how to do without`() {
        val failure = BeanCreationException("store", RedisConnectionException("Unable to connect to localhost:6379"))

        val analysis = RedisUnreachableFailureAnalyzer().analyze(failure)!!

        analysis.description.assert().contains("localhost:6379")
        analysis.action.assert().contains("COSKY_STORE_REDIS_URI").contains("--dev")
    }

    @Test
    fun `a malformed bootstrap token stops the start, with one to use instead`() {
        val failure = runCatching { runBlocking { AuthBootstrap(MemoryStore()).run("mysecret") } }.exceptionOrNull()

        failure.assert().isInstanceOf(StartupFailure::class.java)
        val analysis = StartupFailureAnalyzer().analyze(BeanCreationException("bootstrap", failure!!))!!
        analysis.description.assert().contains("cosky_<id>_<secret>")
        analysis.action.assert().contains("COSKY_BOOTSTRAP_TOKEN").contains("openssl rand")
    }
}
