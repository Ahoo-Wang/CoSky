package me.ahoo.govern.config.redis;

import io.lettuce.core.api.async.RedisScriptingAsyncCommands;
import me.ahoo.govern.core.util.RedisScripts;

import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
public final class ConfigRedisScripts {
    public static final String CONFIG_SET = "config_set.lua";
    public static final String CONFIG_REMOVE = "config_remove.lua";
    public static final String CONFIG_ROLLBACK = "config_rollback.lua";

    public static CompletableFuture<String> loadConfigSet(RedisScriptingAsyncCommands<String, String> scriptingCommands) {
        return RedisScripts.loadScript(CONFIG_SET, scriptingCommands);
    }

    public static CompletableFuture<String> loadConfigRemove(RedisScriptingAsyncCommands<String, String> scriptingCommands) {
        return RedisScripts.loadScript(CONFIG_REMOVE, scriptingCommands);
    }
    public static CompletableFuture<String> loadConfigRollback(RedisScriptingAsyncCommands<String, String> scriptingCommands) {
        return RedisScripts.loadScript(CONFIG_ROLLBACK, scriptingCommands);
    }
}
