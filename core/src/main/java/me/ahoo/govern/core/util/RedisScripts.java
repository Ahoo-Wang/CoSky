package me.ahoo.govern.core.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.async.RedisScriptingAsyncCommands;
import lombok.SneakyThrows;

import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author ahoo wang
 */
public final class RedisScripts {
    private static final String WARN_CLEAR_TEST_DATA = "warn_clear_test_data.lua";
    public static final ConcurrentHashMap<String, CompletableFuture<String>> scriptMapSha = new ConcurrentHashMap<>();

    private RedisScripts() {
    }

    @SneakyThrows
    public static String getScript(String scriptName) {
        URL url = Resources.getResource(scriptName);
        return Resources.toString(url, Charsets.UTF_8);
    }

    public static CompletableFuture<String> loadScript(String scriptName, RedisScriptingAsyncCommands<String, String> scriptingCommands) {
        return scriptMapSha.computeIfAbsent(scriptName, (key) -> {
            String script = getScript(key);
            return scriptingCommands.scriptLoad(script).toCompletableFuture();
        });
    }

    public static CompletableFuture<String> reloadScript(String scriptName, RedisScriptingAsyncCommands<String, String> scriptingCommands) {
        return scriptMapSha.compute(scriptName, (key, result) -> {
            String script = getScript(key);
            return scriptingCommands.scriptLoad(script).toCompletableFuture();
        });
    }


    /**
     * only for dev
     *
     * @param namespace
     * @param scriptingCommands
     * @return
     */
    @Deprecated
    @VisibleForTesting
    public static CompletableFuture<Void> clearTestData(String namespace, RedisScriptingAsyncCommands<String, String> scriptingCommands) {
        return RedisScripts.loadScript(WARN_CLEAR_TEST_DATA, scriptingCommands)
                .thenCompose(sha -> scriptingCommands.evalsha(sha, ScriptOutputType.STATUS, new String[]{namespace}));
    }
}
