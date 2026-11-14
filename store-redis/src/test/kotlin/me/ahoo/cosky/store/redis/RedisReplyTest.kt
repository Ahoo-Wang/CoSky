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

package me.ahoo.cosky.store.redis

import io.lettuce.core.ScriptOutputType
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.output.StatusOutput
import io.lettuce.core.protocol.AsyncCommand
import io.lettuce.core.protocol.Command
import io.lettuce.core.protocol.CommandType
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.store.StoreUnavailableException
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.cancellation.CancellationException

class RedisReplyTest {
    @Test
    fun `a command the driver cancels is the store being unavailable`() = runBlocking<Unit> {
        val cancelled = CompletableFuture<String>().apply { cancel(false) }

        runCatching { cancelled.reply() }.exceptionOrNull()
            .assert().isInstanceOf(StoreUnavailableException::class.java)
    }

    @Test
    fun `a script the driver cancels is the store being unavailable`() = runBlocking<Unit> {
        val cancelled = AsyncCommand(Command(CommandType.EVALSHA, StatusOutput(StringCodec.UTF8))).apply { cancel() }
        val commands = Proxy.newProxyInstance(
            RedisClusterAsyncCommands::class.java.classLoader,
            arrayOf(RedisClusterAsyncCommands::class.java),
        ) { _, _, _ -> cancelled } as RedisClusterAsyncCommands<String, ByteArray>

        runCatching { RedisScript("tail").run<Any>(commands, ScriptOutputType.MULTI, listOf("key"), emptyList()) }
            .exceptionOrNull().assert().isInstanceOf(StoreUnavailableException::class.java)
    }

    @Test
    fun `the caller being cancelled stays cancellation`() = runBlocking<Unit> {
        var thrown: Throwable? = null
        val waiting = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                CompletableFuture<String>().reply()
            } catch (e: CancellationException) {
                thrown = e
                throw e
            }
        }

        waiting.cancelAndJoin()

        thrown.assert().isInstanceOf(CancellationException::class.java)
            .isNotInstanceOf(StoreUnavailableException::class.java)
    }
}
