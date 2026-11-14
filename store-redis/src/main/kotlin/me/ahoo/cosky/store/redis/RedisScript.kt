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

import io.lettuce.core.RedisException
import io.lettuce.core.RedisNoScriptException
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.future.await
import me.ahoo.cosky.store.StoreUnavailableException
import java.security.MessageDigest
import java.util.concurrent.CompletionStage
import kotlin.coroutines.cancellation.CancellationException

/**
 * A Lua script from the classpath, run by digest; it is loaded when a node does not have it yet, as after a failover.
 */
internal class RedisScript(name: String, vararg includes: String) {
    private val source: ByteArray = (includes.toList() + name).map(::read).reduce(ByteArray::plus)

    private val digest: String = MessageDigest.getInstance("SHA-1").digest(source)
        .joinToString("") { "%02x".format(it) }

    private fun read(script: String): ByteArray = checkNotNull(
        RedisScript::class.java.getResourceAsStream("/cosky/store/redis/$script.lua"),
    ) { "Script $script is missing." }.use { it.readBytes() }

    suspend fun <T : Any> run(
        commands: RedisClusterAsyncCommands<String, ByteArray>,
        output: ScriptOutputType,
        keys: List<String>,
        args: List<ByteArray>,
    ): T {
        val keyArray = keys.toTypedArray()
        val argArray = args.toTypedArray()
        return try {
            commands.evalsha<T>(digest, output, keyArray, *argArray).await()
        } catch (_: RedisNoScriptException) {
            commands.eval<T>(source, output, keyArray, *argArray).reply()
        } catch (e: RedisException) {
            throw unavailable(e)
        } catch (e: CancellationException) {
            throw cancelled(e)
        }
    }
}

/**
 * The answer to a command. Lettuce's failures stop here: past the store, Redis being out of reach is the store being
 * unavailable, whichever driver says so.
 */
internal suspend fun <T> CompletionStage<T>.reply(): T = try {
    await()
} catch (e: RedisException) {
    throw unavailable(e)
} catch (e: CancellationException) {
    throw cancelled(e)
}

private fun unavailable(e: RedisException) = StoreUnavailableException("Redis: ${e.message}", e)

/**
 * Lettuce cancels the commands waiting on a connection it closes, as when it follows a failover to another node. That
 * is a command failing, which must not pass for the caller being cancelled: a loop would end as if asked to. Only the
 * caller's own cancellation goes on as cancellation.
 */
private suspend fun cancelled(e: CancellationException): StoreUnavailableException {
    currentCoroutineContext().ensureActive()
    return StoreUnavailableException("Redis: the command was cancelled", e)
}

internal fun String.arg(): ByteArray = encodeToByteArray()

internal fun Number.arg(): ByteArray = toString().encodeToByteArray()

internal fun Any?.asText(): String = (this as ByteArray).decodeToString()

@Suppress("UNCHECKED_CAST")
internal fun Any?.asList(): List<Any?> = this as List<Any?>
