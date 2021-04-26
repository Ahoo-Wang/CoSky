package me.ahoo.govern.discovery.redis;

import io.lettuce.core.api.async.RedisScriptingAsyncCommands;
import me.ahoo.govern.core.util.RedisScripts;

import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
public final class DiscoveryRedisScripts {
    public static final String REGISTRY_REGISTER = "registry_register.lua";
    public static final String REGISTRY_REGISTER_INSTANCE = "registry_register_instance.lua";
    public static final String REGISTRY_DEREGISTER = "registry_deregister.lua";
    public static final String DISCOVERY_GET_INSTANCES = "discovery_getInstances.lua";


    public static CompletableFuture<String> loadRegistryRegister(RedisScriptingAsyncCommands<String, String> scriptingCommands) {
        return RedisScripts.loadScript(REGISTRY_REGISTER, scriptingCommands);
    }

    public static CompletableFuture<String> loadRegistryRegisterInstance(RedisScriptingAsyncCommands<String, String> scriptingCommands) {
        return RedisScripts.loadScript(REGISTRY_REGISTER_INSTANCE, scriptingCommands);
    }

    public static CompletableFuture<String> loadRegistryDeregister(RedisScriptingAsyncCommands<String, String> scriptingCommands) {
        return RedisScripts.loadScript(REGISTRY_DEREGISTER, scriptingCommands);
    }

    public static CompletableFuture<String> loadDiscoveryGetInstances(RedisScriptingAsyncCommands<String, String> scriptingCommands) {
        return RedisScripts.loadScript(DISCOVERY_GET_INSTANCES, scriptingCommands);
    }


}
