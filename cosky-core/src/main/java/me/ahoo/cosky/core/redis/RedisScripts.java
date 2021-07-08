/*
 *
 *  * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package me.ahoo.cosky.core.redis;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.common.io.Resources;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisNoScriptException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.async.RedisScriptingAsyncCommands;
import io.lettuce.core.internal.Exceptions;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * @author ahoo wang
 */
@Slf4j
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

    public static void clearScript() {
        if (log.isInfoEnabled()) {
            log.info("clearScript");
        }
        scriptMapSha.clear();
    }

    public static CompletableFuture<String> loadScript(String scriptName, RedisScriptingAsyncCommands<String, String> scriptingCommands) {

        return scriptMapSha.computeIfAbsent(scriptName, (key) -> {
            if (log.isInfoEnabled()) {
                log.info("loadScript - scriptName : [{}].", scriptName);
            }
            String script = getScript(key);

            String scriptSha = Hashing.sha1().hashString(script, Charsets.UTF_8).toString();
            return scriptingCommands.scriptExists(scriptSha).thenCompose(existsList -> {
                boolean isExists = existsList.get(0);
                if (isExists) {
                    return CompletableFuture.completedFuture(scriptSha);
                }
                return scriptingCommands.scriptLoad(script).toCompletableFuture();
            }).toCompletableFuture();
        });
    }

    /**
     * 当 Redis 宕机恢复时，lua 脚本需要重新加载
     *
     * @param scriptName
     * @param scriptingCommands
     * @return
     */
    public static CompletableFuture<String> reloadScript(String scriptName, RedisScriptingAsyncCommands<String, String> scriptingCommands) {
        return scriptMapSha.compute(scriptName, (key, result) -> {
            if (log.isInfoEnabled()) {
                log.info("reloadScript - scriptName : [{}].", scriptName);
            }
            String script = getScript(key);
            return scriptingCommands.scriptLoad(script).toCompletableFuture();
        });
    }

    public static <T> CompletableFuture<T> doEnsureScript(String scriptName, RedisScriptingAsyncCommands<String, String> scriptingCommands, Function<String, RedisFuture<T>> doSha) {
        CompletableFuture<T> ensureFuture = new CompletableFuture<>();
        loadScript(scriptName, scriptingCommands)
                .whenComplete((sha, loadScriptException) -> {

                    if (Objects.nonNull(loadScriptException)) {
                        ensureFuture.completeExceptionally(loadScriptException);
                        return;
                    }

                    doSha.apply(sha).whenComplete((result, throwable) -> {
                        if (Objects.isNull(throwable)) {
                            ensureFuture.complete(result);
                            return;
                        }

                        boolean isRedisNoScript = Exceptions.unwrap(throwable) instanceof RedisNoScriptException;

                        if (!isRedisNoScript) {
                            ensureFuture.completeExceptionally(throwable);
                            return;
                        }

                        reloadScript(scriptName, scriptingCommands).whenComplete((reloadSha, reloadException) -> {

                            if (Objects.nonNull(reloadException)) {
                                ensureFuture.completeExceptionally(reloadException);
                                return;
                            }

                            doSha.apply(reloadSha).whenComplete((retryResult, retryException) -> {
                                if (Objects.nonNull(retryException)) {
                                    ensureFuture.completeExceptionally(retryException);
                                    return;
                                }
                                ensureFuture.complete(retryResult);
                            });
                        });
                    });
                });

        return ensureFuture;
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
        if (log.isWarnEnabled()) {
            log.warn("clearTestData - namespace : [{}].", namespace);
        }
        return RedisScripts.loadScript(WARN_CLEAR_TEST_DATA, scriptingCommands)
                .thenCompose(sha -> scriptingCommands.evalsha(sha, ScriptOutputType.STATUS, new String[]{namespace}));
    }
}
