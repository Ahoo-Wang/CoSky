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

import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Redis commands that [answer] answers, for a test to make the driver fail as it pleases. Those of `Object` behave as
 * any object's, so that printing or comparing them in a failure says what went wrong.
 */
@Suppress("UNCHECKED_CAST")
internal fun commandsAnswering(
    answer: (method: Method, args: Array<Any?>) -> Any?,
): RedisClusterAsyncCommands<String, ByteArray> = Proxy.newProxyInstance(
    RedisClusterAsyncCommands::class.java.classLoader,
    arrayOf(RedisClusterAsyncCommands::class.java),
) { proxy, method, args ->
    if (method.declaringClass == Any::class.java) {
        when (method.name) {
            "equals" -> proxy === args?.firstOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            else -> "Redis commands of a test"
        }
    } else {
        answer(method, args ?: emptyArray())
    }
} as RedisClusterAsyncCommands<String, ByteArray>
