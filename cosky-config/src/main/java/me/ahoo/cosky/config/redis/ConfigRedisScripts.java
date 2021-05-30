package me.ahoo.cosky.config.redis;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisScriptingAsyncCommands;
import me.ahoo.cosky.core.util.RedisScripts;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * @author ahoo wang
 */
public final class ConfigRedisScripts {
    public static final String CONFIG_SET = "config_set.lua";
    public static final String CONFIG_REMOVE = "config_remove.lua";
    public static final String CONFIG_ROLLBACK = "config_rollback.lua";

    public static <T> CompletableFuture<T> doConfigSet(RedisScriptingAsyncCommands<String, String> scriptingCommands, Function<String, RedisFuture<T>> doSha) {
        return RedisScripts.doEnsureScript(CONFIG_SET, scriptingCommands, doSha);
    }
    public static <T> CompletableFuture<T> doConfigRemove(RedisScriptingAsyncCommands<String, String> scriptingCommands, Function<String, RedisFuture<T>> doSha) {
        return RedisScripts.doEnsureScript(CONFIG_REMOVE, scriptingCommands, doSha);
    }
    public static <T> CompletableFuture<T> doConfigRollback(RedisScriptingAsyncCommands<String, String> scriptingCommands, Function<String, RedisFuture<T>> doSha) {
        return RedisScripts.doEnsureScript(CONFIG_ROLLBACK, scriptingCommands, doSha);
    }

}
