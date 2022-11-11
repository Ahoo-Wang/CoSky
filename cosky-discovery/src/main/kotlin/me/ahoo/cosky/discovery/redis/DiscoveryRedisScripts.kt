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

import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.data.redis.core.script.RedisScript

/**
 * Discovery Redis Scripts.
 *
 * @author ahoo wang
 */
object DiscoveryRedisScripts {
    private val RESOURCE_REGISTRY_REGISTER: Resource = ClassPathResource("registry_register.lua")
    val SCRIPT_REGISTRY_REGISTER: RedisScript<Boolean> = RedisScript.of(RESOURCE_REGISTRY_REGISTER, Boolean::class.java)

    private val RESOURCE_REGISTRY_DEREGISTER: Resource = ClassPathResource("registry_deregister.lua")
    val SCRIPT_REGISTRY_DEREGISTER: RedisScript<Boolean> =
        RedisScript.of(RESOURCE_REGISTRY_DEREGISTER, Boolean::class.java)

    private val RESOURCE_REGISTRY_RENEW: Resource = ClassPathResource("registry_renew.lua")
    val SCRIPT_REGISTRY_RENEW: RedisScript<Long> = RedisScript.of(RESOURCE_REGISTRY_RENEW, Long::class.java)

    private val RESOURCE_REGISTRY_SET_METADATA: Resource = ClassPathResource("registry_set_metadata.lua")
    val SCRIPT_REGISTRY_SET_METADATA: RedisScript<Boolean> =
        RedisScript.of(RESOURCE_REGISTRY_SET_METADATA, Boolean::class.java)

    private val RESOURCE_REGISTRY_SET_SERVICE: Resource = ClassPathResource("registry_set_service.lua")
    val SCRIPT_REGISTRY_SET_SERVICE: RedisScript<Boolean> =
        RedisScript.of(RESOURCE_REGISTRY_SET_SERVICE, Boolean::class.java)

    private val RESOURCE_REGISTRY_REMOVE_SERVICE: Resource = ClassPathResource("registry_remove_service.lua")
    val SCRIPT_REGISTRY_REMOVE_SERVICE: RedisScript<Boolean> =
        RedisScript.of(RESOURCE_REGISTRY_REMOVE_SERVICE, Boolean::class.java)

    private val RESOURCE_REGISTRY_GET_INSTANCES: Resource = ClassPathResource("discovery_get_instances.lua")
    val SCRIPT_REGISTRY_GET_INSTANCES: RedisScript<List<*>> =
        RedisScript.of(RESOURCE_REGISTRY_GET_INSTANCES, List::class.java)

    private val RESOURCE_REGISTRY_GET_INSTANCE: Resource = ClassPathResource("discovery_get_instance.lua")
    val SCRIPT_REGISTRY_GET_INSTANCE: RedisScript<List<*>> =
        RedisScript.of(RESOURCE_REGISTRY_GET_INSTANCE, List::class.java)

    private val RESOURCE_REGISTRY_GET_INSTANCE_TTL: Resource = ClassPathResource("discovery_get_instance_ttl.lua")
    val SCRIPT_REGISTRY_GET_INSTANCE_TTL: RedisScript<Long> =
        RedisScript.of(RESOURCE_REGISTRY_GET_INSTANCE_TTL, Long::class.java)

    private val RESOURCE_INSTANCE_COUNT_STAT: Resource = ClassPathResource("instance_count_stat.lua")
    val SCRIPT_INSTANCE_COUNT_STAT: RedisScript<Long> = RedisScript.of(RESOURCE_INSTANCE_COUNT_STAT, Long::class.java)

    private val RESOURCE_SERVICE_STAT: Resource = ClassPathResource("service_stat.lua")
    val SCRIPT_SERVICE_STAT: RedisScript<Boolean> = RedisScript.of(RESOURCE_SERVICE_STAT, Boolean::class.java)

    private val RESOURCE_TOPOLOGY_ADD: Resource = ClassPathResource("service_topology_add.lua")
    val SCRIPT_TOPOLOGY_ADD: RedisScript<Boolean> = RedisScript.of(RESOURCE_TOPOLOGY_ADD, Boolean::class.java)

    private val RESOURCE_SERVICE_TOPOLOGY_GET: Resource = ClassPathResource("service_topology_get.lua")
    val SCRIPT_SERVICE_TOPOLOGY_GET: RedisScript<List<*>> =
        RedisScript.of(RESOURCE_SERVICE_TOPOLOGY_GET, List::class.java)
}
