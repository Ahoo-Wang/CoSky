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

package me.ahoo.cosky.config.redis;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * ConfigRedisScripts.
 *
 * @author ahoo wang
 */
public final class ConfigRedisScripts {
    public static final Resource RESOURCE_CONFIG_SET = new ClassPathResource("config_set.lua");
    public static final RedisScript<Boolean> SCRIPT_CONFIG_SET = RedisScript.of(RESOURCE_CONFIG_SET, Boolean.class);
    
    public static final Resource RESOURCE_CONFIG_REMOVE = new ClassPathResource("config_remove.lua");
    public static final RedisScript<Boolean> SCRIPT_CONFIG_REMOVE = RedisScript.of(RESOURCE_CONFIG_REMOVE, Boolean.class);
    
    public static final Resource RESOURCE_CONFIG_ROLLBACK = new ClassPathResource("config_rollback.lua");
    public static final RedisScript<Boolean> SCRIPT_CONFIG_ROLLBACK = RedisScript.of(RESOURCE_CONFIG_ROLLBACK, Boolean.class);
}
