package me.ahoo.govern.discovery.redis;

import io.lettuce.core.api.async.RedisScriptingAsyncCommands;
import me.ahoo.govern.core.util.RedisScripts;

import java.util.concurrent.CompletableFuture;

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

    public static CompletableFuture<String> loadRegistryRegister(RedisScriptingAsyncCommands<String, String> scriptingCommands) {
        return RedisScripts.loadScript(REGISTRY_REGISTER, scriptingCommands);
    }

    public static CompletableFuture<String> loadRegistryDeregister(RedisScriptingAsyncCommands<String, String> scriptingCommands) {
        return RedisScripts.loadScript(REGISTRY_DEREGISTER, scriptingCommands);
    }

    public static CompletableFuture<String> loadRegistryRenew(RedisScriptingAsyncCommands<String, String> scriptingCommands) {
        return RedisScripts.loadScript(REGISTRY_RENEW, scriptingCommands);
    }

    public static CompletableFuture<String> loadRegistrySetMetadata(RedisScriptingAsyncCommands<String, String> scriptingCommands) {
        return RedisScripts.loadScript(REGISTRY_SET_METADATA, scriptingCommands);
    }

    public static CompletableFuture<String> loadRegistrySetService(RedisScriptingAsyncCommands<String, String> scriptingCommands) {
        return RedisScripts.loadScript(REGISTRY_SET_SERVICE, scriptingCommands);
    }

    public static CompletableFuture<String> loadRegistryRemoveService(RedisScriptingAsyncCommands<String, String> scriptingCommands) {
        return RedisScripts.loadScript(REGISTRY_REMOVE_SERVICE, scriptingCommands);
    }

    public static CompletableFuture<String> loadDiscoveryGetInstances(RedisScriptingAsyncCommands<String, String> scriptingCommands) {
        return RedisScripts.loadScript(DISCOVERY_GET_INSTANCES, scriptingCommands);
    }

    public static CompletableFuture<String> loadDiscoveryGetInstance(RedisScriptingAsyncCommands<String, String> scriptingCommands) {
        return RedisScripts.loadScript(DISCOVERY_GET_INSTANCE, scriptingCommands);
    }

    public static CompletableFuture<String> loadDiscoveryGetInstanceTtl(RedisScriptingAsyncCommands<String, String> scriptingCommands) {
        return RedisScripts.loadScript(DISCOVERY_GET_INSTANCE_TTL, scriptingCommands);
    }

    public static CompletableFuture<String> loadServiceStat(RedisScriptingAsyncCommands<String, String> scriptingCommands) {
        return RedisScripts.loadScript(SERVICE_STAT, scriptingCommands);
    }

    public static CompletableFuture<String> loadInstanceCountStat(RedisScriptingAsyncCommands<String, String> scriptingCommands) {
        return RedisScripts.loadScript(INSTANCE_COUNT_STAT, scriptingCommands);
    }
}
