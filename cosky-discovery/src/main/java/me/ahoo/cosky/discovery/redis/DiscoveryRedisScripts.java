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

package me.ahoo.cosky.discovery.redis;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * Discovery Redis Scripts.
 *
 * @author ahoo wang
 */
public final class DiscoveryRedisScripts {
    
    public static final Resource RESOURCE_REGISTRY_REGISTER = new ClassPathResource("registry_register.lua");
    public static final RedisScript<Boolean> SCRIPT_REGISTRY_REGISTER = RedisScript.of(RESOURCE_REGISTRY_REGISTER, Boolean.class);
    
    public static final Resource RESOURCE_REGISTRY_DEREGISTER = new ClassPathResource("registry_deregister.lua");
    public static final RedisScript<Boolean> SCRIPT_REGISTRY_DEREGISTER = RedisScript.of(RESOURCE_REGISTRY_DEREGISTER, Boolean.class);
    
    public static final Resource RESOURCE_REGISTRY_RENEW = new ClassPathResource("registry_renew.lua");
    public static final RedisScript<Long> SCRIPT_REGISTRY_RENEW = RedisScript.of(RESOURCE_REGISTRY_RENEW, Long.class);
    
    public static final Resource RESOURCE_REGISTRY_SET_METADATA = new ClassPathResource("registry_set_metadata.lua");
    public static final RedisScript<Boolean> SCRIPT_REGISTRY_SET_METADATA = RedisScript.of(RESOURCE_REGISTRY_SET_METADATA, Boolean.class);

    public static final Resource RESOURCE_REGISTRY_SET_SERVICE = new ClassPathResource("registry_set_service.lua");
    public static final RedisScript<Boolean> SCRIPT_REGISTRY_SET_SERVICE = RedisScript.of(RESOURCE_REGISTRY_SET_SERVICE, Boolean.class);

    public static final Resource RESOURCE_REGISTRY_REMOVE_SERVICE = new ClassPathResource("registry_remove_service.lua");
    public static final RedisScript<Boolean> SCRIPT_REGISTRY_REMOVE_SERVICE = RedisScript.of(RESOURCE_REGISTRY_REMOVE_SERVICE, Boolean.class);

    public static final Resource RESOURCE_REGISTRY_GET_INSTANCES = new ClassPathResource("discovery_get_instances.lua");
    public static final RedisScript<List> SCRIPT_REGISTRY_GET_INSTANCES = RedisScript.of(RESOURCE_REGISTRY_GET_INSTANCES, List.class);

    public static final Resource RESOURCE_REGISTRY_GET_INSTANCE = new ClassPathResource("discovery_get_instance.lua");
    public static final RedisScript<List> SCRIPT_REGISTRY_GET_INSTANCE = RedisScript.of(RESOURCE_REGISTRY_GET_INSTANCE, List.class);

    public static final Resource RESOURCE_REGISTRY_GET_INSTANCE_TTL = new ClassPathResource("discovery_get_instance_ttl.lua");
    public static final RedisScript<Long> SCRIPT_REGISTRY_GET_INSTANCE_TTL = RedisScript.of(RESOURCE_REGISTRY_GET_INSTANCE_TTL, Long.class);
    
    public static final Resource RESOURCE_INSTANCE_COUNT_STAT = new ClassPathResource("instance_count_stat.lua");
    public static final RedisScript<Long> SCRIPT_INSTANCE_COUNT_STAT = RedisScript.of(RESOURCE_INSTANCE_COUNT_STAT, Long.class);

    public static final Resource RESOURCE_SERVICE_STAT = new ClassPathResource("service_stat.lua");
    public static final RedisScript<Boolean> SCRIPT_SERVICE_STAT = RedisScript.of(RESOURCE_SERVICE_STAT, Boolean.class);
    
    public static final Resource RESOURCE_TOPOLOGY_ADD = new ClassPathResource("service_topology_add.lua");
    public static final RedisScript<Boolean> SCRIPT_TOPOLOGY_ADD = RedisScript.of(RESOURCE_TOPOLOGY_ADD, Boolean.class);
    
    public static final Resource RESOURCE_SERVICE_TOPOLOGY_GET = new ClassPathResource("service_topology_get.lua");
    public static final RedisScript<List> SCRIPT_SERVICE_TOPOLOGY_GET = RedisScript.of(RESOURCE_SERVICE_TOPOLOGY_GET, List.class);

}
