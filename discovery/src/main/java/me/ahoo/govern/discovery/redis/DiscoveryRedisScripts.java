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
    public static final String DISCOVERY_GET_INSTANCES = "discovery_get_instances.lua";
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

    public static CompletableFuture<String> loadDiscoveryGetInstances(RedisScriptingAsyncCommands<String, String> scriptingCommands) {
        return RedisScripts.loadScript(DISCOVERY_GET_INSTANCES, scriptingCommands);
    }

    public static CompletableFuture<String> loadServiceStat(RedisScriptingAsyncCommands<String, String> scriptingCommands) {
        return RedisScripts.loadScript(SERVICE_STAT, scriptingCommands);
    }
}
