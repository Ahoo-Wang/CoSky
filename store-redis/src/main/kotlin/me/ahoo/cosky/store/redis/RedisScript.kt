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
import kotlinx.coroutines.future.await
import java.security.MessageDigest

/**
 * A Lua script from the classpath, run by digest; it is loaded when a node does not have it yet, as after a failover.
 */
internal class RedisScript(name: String) {
    private val source: ByteArray = checkNotNull(
        RedisScript::class.java.getResourceAsStream("/cosky/store/redis/$name.lua"),
    ) { "Script $name is missing." }.use { it.readBytes() }

    private val digest: String = MessageDigest.getInstance("SHA-1").digest(source)
        .joinToString("") { "%02x".format(it) }

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
            commands.eval<T>(source, output, keyArray, *argArray).await()
        }
    }
}

internal fun String.arg(): ByteArray = encodeToByteArray()

internal fun Number.arg(): ByteArray = toString().encodeToByteArray()

internal fun Any?.asText(): String = (this as ByteArray).decodeToString()

@Suppress("UNCHECKED_CAST")
internal fun Any?.asList(): List<Any?> = this as List<Any?>
