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
package me.ahoo.cosky.core.redis

import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.script.RedisScript
import java.nio.charset.StandardCharsets

/**
 * Loads Lua scripts from the classpath, resolving `-- @include <resource>` directives at load time so that scripts
 * can share functions without Redis Functions.
 *
 * Each resource is included at most once per script, before the code that includes it.
 *
 * @author ahoo wang
 */
object LuaScripts {
    private val INCLUDE = Regex("""^--\s*@include\s+(\S+)$""")

    fun <T : Any> load(resource: String, resultType: Class<T>): RedisScript<T> {
        return RedisScript.of(source(resource), resultType)
    }

    fun source(resource: String): String {
        return StringBuilder().also { resolve(resource, mutableSetOf(), ArrayDeque(), it) }.toString()
    }

    private fun resolve(resource: String, included: MutableSet<String>, stack: ArrayDeque<String>, out: StringBuilder) {
        check(resource !in stack) { "Cyclic Lua include: ${(stack + resource).joinToString(" -> ")}" }
        if (!included.add(resource)) {
            return
        }
        stack.addLast(resource)
        val classPathResource = ClassPathResource(resource)
        require(classPathResource.exists()) { "Lua script resource:[$resource] not found." }
        classPathResource.inputStream.use { it.readAllBytes().toString(StandardCharsets.UTF_8) }
            .lineSequence()
            .forEach { line ->
                val include = INCLUDE.matchEntire(line.trim())
                if (include == null) {
                    out.append(line).append('\n')
                } else {
                    resolve(include.groupValues[1], included, stack, out)
                }
            }
        stack.removeLast()
    }
}
