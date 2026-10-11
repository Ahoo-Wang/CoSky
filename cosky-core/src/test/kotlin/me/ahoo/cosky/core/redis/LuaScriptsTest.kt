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

import me.ahoo.cosky.test.AbstractReactiveRedisTest
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import reactor.kotlin.test.test

class LuaScriptsTest : AbstractReactiveRedisTest() {

    @Test
    fun includeOnceInDependencyOrder() {
        val source = LuaScripts.source("lua-test/main.lua")
        source.contains("@include").assert().isFalse()
        Regex("local function b").findAll(source).count().assert().isEqualTo(1)
        (source.indexOf("local function b") < source.indexOf("local function a")).assert().isTrue()
    }

    @Test
    fun includedScriptRunsOnRedis() {
        redisTemplate.execute(LuaScripts.load("lua-test/main.lua", String::class.java))
            .test()
            .expectNext("ba")
            .verifyComplete()
    }

    @Test
    fun cyclicInclude() {
        assertThrows<IllegalStateException> { LuaScripts.source("lua-test/cycle_a.lua") }
    }

    @Test
    fun missingResource() {
        assertThrows<IllegalArgumentException> { LuaScripts.source("lua-test/missing.lua") }
    }
}
