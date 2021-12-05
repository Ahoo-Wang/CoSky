/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

import io.lettuce.core.api.reactive.RedisScriptingReactiveCommands;
import me.ahoo.cosky.core.redis.RedisScripts;
import reactor.core.publisher.Mono;


import java.util.function.Function;

/**
 * @author ahoo wang
 */
public final class DiscoveryRedisScripts {
    public static final String REGISTRY_REGISTER = "registry_register.lua";
    public static final String REGISTRY_DEREGISTER = "registry_deregister.lua";
    public static final String REGISTRY_RENEW = "registry_renew.lua";
    public static final String REGISTRY_SET_METADATA = "registry_set_metadata.lua";
    public static final String REGISTRY_SET_SERVICE = "registry_set_service.lua";
    public static final String REGISTRY_REMOVE_SERVICE = "registry_remove_service.lua";
    public static final String DISCOVERY_GET_INSTANCES = "discovery_get_instances.lua";
    public static final String DISCOVERY_GET_INSTANCE = "discovery_get_instance.lua";
    public static final String DISCOVERY_GET_INSTANCE_TTL = "discovery_get_instance_ttl.lua";
    public static final String INSTANCE_COUNT_STAT = "instance_count_stat.lua";
    public static final String SERVICE_STAT = "service_stat.lua";
    public static final String SERVICE_TOPOLOGY_ADD = "service_topology_add.lua";

    public static <T> Mono<T> doRegistryRegister(RedisScriptingReactiveCommands<String, String> scriptingCommands, Function<String, Mono<T>> doSha) {
        return RedisScripts.doEnsureScript(REGISTRY_REGISTER, scriptingCommands, doSha);
    }

    public static <T> Mono<T> doRegistryDeregister(RedisScriptingReactiveCommands<String, String> scriptingCommands, Function<String, Mono<T>> doSha) {
        return RedisScripts.doEnsureScript(REGISTRY_DEREGISTER, scriptingCommands, doSha);
    }

    public static <T> Mono<T> doRegistryRenew(RedisScriptingReactiveCommands<String, String> scriptingCommands, Function<String, Mono<T>> doSha) {
        return RedisScripts.doEnsureScript(REGISTRY_RENEW, scriptingCommands, doSha);
    }

    public static <T> Mono<T> doRegistrySetMetadata(RedisScriptingReactiveCommands<String, String> scriptingCommands, Function<String, Mono<T>> doSha) {
        return RedisScripts.doEnsureScript(REGISTRY_SET_METADATA, scriptingCommands, doSha);
    }

    public static <T> Mono<T> doRegistrySetService(RedisScriptingReactiveCommands<String, String> scriptingCommands, Function<String, Mono<T>> doSha) {
        return RedisScripts.doEnsureScript(REGISTRY_SET_SERVICE, scriptingCommands, doSha);
    }

    public static <T> Mono<T> doRegistryRemoveService(RedisScriptingReactiveCommands<String, String> scriptingCommands, Function<String, Mono<T>> doSha) {
        return RedisScripts.doEnsureScript(REGISTRY_REMOVE_SERVICE, scriptingCommands, doSha);
    }

    public static <T> Mono<T> doDiscoveryGetInstances(RedisScriptingReactiveCommands<String, String> scriptingCommands, Function<String, Mono<T>> doSha) {
        return RedisScripts.doEnsureScript(DISCOVERY_GET_INSTANCES, scriptingCommands, doSha);
    }

    public static <T> Mono<T> doDiscoveryGetInstance(RedisScriptingReactiveCommands<String, String> scriptingCommands, Function<String, Mono<T>> doSha) {
        return RedisScripts.doEnsureScript(DISCOVERY_GET_INSTANCE, scriptingCommands, doSha);
    }

    public static <T> Mono<T> doDiscoveryGetInstanceTtl(RedisScriptingReactiveCommands<String, String> scriptingCommands, Function<String, Mono<T>> doSha) {
        return RedisScripts.doEnsureScript(DISCOVERY_GET_INSTANCE_TTL, scriptingCommands, doSha);
    }

    public static <T> Mono<T> doServiceStat(RedisScriptingReactiveCommands<String, String> scriptingCommands, Function<String, Mono<T>> doSha) {
        return RedisScripts.doEnsureScript(SERVICE_STAT, scriptingCommands, doSha);
    }
    public static Mono<String> loadInstanceCountStat(RedisScriptingReactiveCommands<String, String> scriptingCommands) {
        return RedisScripts.loadScript(INSTANCE_COUNT_STAT, scriptingCommands);
    }
    public static <T> Mono<T> doServiceTopologyAdd(RedisScriptingReactiveCommands<String, String> scriptingCommands, Function<String, Mono<T>> doSha) {
        return RedisScripts.doEnsureScript(SERVICE_TOPOLOGY_ADD, scriptingCommands, doSha);
    }
}
