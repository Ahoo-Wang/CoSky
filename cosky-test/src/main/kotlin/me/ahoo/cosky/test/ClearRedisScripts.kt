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
package me.ahoo.cosky.test

import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import reactor.core.publisher.Mono

/**
 * ClearRedisScripts .
 *
 * @author ahoo wang
 */
object ClearRedisScripts {
    val RESOURCE_CLEAN: Resource = ClassPathResource("warn_clear_test_data.lua")
    val SCRIPT_CLEAN = RedisScript.of(RESOURCE_CLEAN, Void::class.java)

    @JvmStatic
    fun clear(redisTemplate: ReactiveStringRedisTemplate, prefix: String): Mono<Void> {
        return redisTemplate.execute(
            SCRIPT_CLEAN,
            listOf(prefix)
        ).next()
    }
}
