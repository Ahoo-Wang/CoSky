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

import io.lettuce.core.RedisNoScriptException
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
import java.net.SocketException
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
    fun `a command on a connection reset is the store being unavailable`() = runBlocking<Unit> {
        val reset = CompletableFuture<String>().apply { completeExceptionally(SocketException("Connection reset")) }

        runCatching { reset.reply() }.exceptionOrNull()
            .assert().isInstanceOf(StoreUnavailableException::class.java)
    }

    @Test
    fun `a connection reset the driver wraps is the store being unavailable`() = runBlocking<Unit> {
        val wrapped = RuntimeException("Decoding failed", SocketException("Connection reset"))
        val reset = CompletableFuture<String>().apply { completeExceptionally(wrapped) }

        val error = runCatching { reset.reply() }.exceptionOrNull()

        error.assert().isInstanceOf(StoreUnavailableException::class.java)
        error?.cause.assert().isSameAs(wrapped)
    }

    @Test
    fun `a failure that is not redis being out of reach goes on as it is`() = runBlocking<Unit> {
        val bug = IllegalStateException("Not the store's doing")
        val failed = CompletableFuture<String>().apply { completeExceptionally(bug) }

        runCatching { failed.reply() }.exceptionOrNull().assert().isSameAs(bug)
    }

    @Test
    fun `a script on a connection reset is the store being unavailable`() = runBlocking<Unit> {
        val commands = commandsAnswering { method, _ ->
            check(method.name == "evalsha") { "Only evalsha was expected, not ${method.name}." }
            command().apply { completeExceptionally(SocketException("Connection reset")) }
        }

        runCatching { runScript(commands) }.exceptionOrNull()
            .assert().isInstanceOf(StoreUnavailableException::class.java)
    }

    @Test
    fun `a script the driver cancels is the store being unavailable`() = runBlocking<Unit> {
        val commands = commandsAnswering { method, _ ->
            check(method.name == "evalsha") { "Only evalsha was expected, not ${method.name}." }
            command().apply { cancel() }
        }

        runCatching { runScript(commands) }.exceptionOrNull()
            .assert().isInstanceOf(StoreUnavailableException::class.java)
    }

    @Test
    fun `a script loaded again, which the driver cancels, is the store being unavailable`() = runBlocking<Unit> {
        val commands = commandsAnswering { method, _ ->
            when (method.name) {
                "evalsha" -> command().apply { completeExceptionally(RedisNoScriptException("NOSCRIPT")) }
                "eval" -> command().apply { cancel() }
                else -> error("Only evalsha and eval were expected, not ${method.name}.")
            }
        }

        runCatching { runScript(commands) }.exceptionOrNull()
            .assert().isInstanceOf(StoreUnavailableException::class.java)
    }

    @Test
    fun `the caller being cancelled while a script runs stays cancellation`() = runBlocking<Unit> {
        val commands = commandsAnswering { _, _ -> command() }
        var thrown: Throwable? = null
        val waiting = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                runScript(commands)
            } catch (e: CancellationException) {
                thrown = e
                throw e
            }
        }

        waiting.cancelAndJoin()

        thrown.assert().isInstanceOf(CancellationException::class.java)
            .isNotInstanceOf(StoreUnavailableException::class.java)
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

    /** A command no reply has completed yet. */
    private fun command() = AsyncCommand(Command(CommandType.EVALSHA, StatusOutput(StringCodec.UTF8)))

    private suspend fun runScript(commands: RedisClusterAsyncCommands<String, ByteArray>): Any =
        RedisScript("tail").run(commands, ScriptOutputType.MULTI, listOf("key"), emptyList())
}
