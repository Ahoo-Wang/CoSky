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
package me.ahoo.cosky.config.redis

import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.data.redis.core.script.RedisScript

/**
 * Config Redis Scripts.
 *
 * @author ahoo wang
 */
object ConfigRedisScripts {
    private val RESOURCE_CONFIG_SET: Resource = ClassPathResource("config_set.lua")
    val SCRIPT_CONFIG_SET: RedisScript<Boolean> = RedisScript.of(RESOURCE_CONFIG_SET, Boolean::class.java)

    private val RESOURCE_CONFIG_REMOVE: Resource = ClassPathResource("config_remove.lua")
    val SCRIPT_CONFIG_REMOVE: RedisScript<Boolean> = RedisScript.of(RESOURCE_CONFIG_REMOVE, Boolean::class.java)

    private val RESOURCE_CONFIG_ROLLBACK: Resource = ClassPathResource("config_rollback.lua")
    val SCRIPT_CONFIG_ROLLBACK: RedisScript<Boolean> = RedisScript.of(RESOURCE_CONFIG_ROLLBACK, Boolean::class.java)
}
