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
package me.ahoo.cosky.discovery.redis

import me.ahoo.cosky.core.redis.LuaScripts
import org.springframework.data.redis.core.script.RedisScript

/**
 * Discovery Redis Scripts.
 *
 * @author ahoo wang
 */
object DiscoveryRedisScripts {
    val SCRIPT_REGISTRY_REGISTER: RedisScript<Boolean> =
        LuaScripts.load("registry_register.lua", Boolean::class.java)
    val SCRIPT_REGISTRY_DEREGISTER: RedisScript<Boolean> =
        LuaScripts.load("registry_deregister.lua", Boolean::class.java)
    val SCRIPT_REGISTRY_RENEW: RedisScript<Long> =
        LuaScripts.load("registry_renew.lua", Long::class.java)
    val SCRIPT_REGISTRY_SET_METADATA: RedisScript<Boolean> =
        LuaScripts.load("registry_set_metadata.lua", Boolean::class.java)
    val SCRIPT_REGISTRY_SET_SERVICE: RedisScript<Boolean> =
        LuaScripts.load("registry_set_service.lua", Boolean::class.java)
    val SCRIPT_REGISTRY_REMOVE_SERVICE: RedisScript<Boolean> =
        LuaScripts.load("registry_remove_service.lua", Boolean::class.java)
    val SCRIPT_REGISTRY_GET_INSTANCES: RedisScript<List<*>> =
        LuaScripts.load("discovery_get_instances.lua", List::class.java)
    val SCRIPT_REGISTRY_GET_INSTANCE: RedisScript<List<*>> =
        LuaScripts.load("discovery_get_instance.lua", List::class.java)
    val SCRIPT_REGISTRY_GET_INSTANCE_TTL: RedisScript<Long> =
        LuaScripts.load("discovery_get_instance_ttl.lua", Long::class.java)
    val SCRIPT_INSTANCE_COUNT_STAT: RedisScript<Long> =
        LuaScripts.load("instance_count_stat.lua", Long::class.java)
    val SCRIPT_SERVICE_STAT: RedisScript<Boolean> =
        LuaScripts.load("service_stat.lua", Boolean::class.java)
    val SCRIPT_TOPOLOGY_ADD: RedisScript<Boolean> =
        LuaScripts.load("service_topology_add.lua", Boolean::class.java)
    val SCRIPT_SERVICE_TOPOLOGY_GET: RedisScript<List<*>> =
        LuaScripts.load("service_topology_get.lua", List::class.java)
}
