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

package me.ahoo.cosky.core.redis;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Resources;
import io.lettuce.core.RedisNoScriptException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.reactive.RedisScriptingReactiveCommands;
import io.lettuce.core.internal.Exceptions;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosky.core.CoskyException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * @author ahoo wang
 */
@Slf4j
public final class RedisScripts {
    private static final String WARN_CLEAR_TEST_DATA = "warn_clear_test_data.lua";
    public static final ConcurrentHashMap<String, Mono<String>> scriptMapSha = new ConcurrentHashMap<>();

    private RedisScripts() {
    }

    public static byte[] getScript(String scriptName) {
        try {
            URL url = Resources.getResource(scriptName);
            return Resources.toByteArray(url);
        } catch (IOException ioException) {
            if (log.isErrorEnabled()) {
                log.error(ioException.getMessage(), ioException);
            }
            throw new CoskyException(ioException.getMessage(), ioException);
        }
    }

    public static void clearScript() {
        if (log.isInfoEnabled()) {
            log.info("clearScript");
        }
        scriptMapSha.clear();
    }

    public static Mono<String> loadScript(String scriptName, RedisScriptingReactiveCommands<String, String> scriptingCommands) {
        return scriptMapSha.computeIfAbsent(scriptName, (key) -> {
            if (log.isInfoEnabled()) {
                log.info("loadScript - scriptName : [{}].", scriptName);
            }
            return tryGetSha(key, scriptingCommands);
        });
    }

    private static Mono<String> tryGetSha(String scriptName, RedisScriptingReactiveCommands<String, String> scriptingCommands) {
        byte[] script = getScript(scriptName);
        return scriptingCommands.scriptLoad(script).cache();
    }

    /**
     * 当 Redis 宕机恢复时，lua 脚本需要重新加载
     *
     * @param scriptName
     * @param scriptingCommands
     * @return
     */
    public static Mono<String> reloadScript(String scriptName, RedisScriptingReactiveCommands<String, String> scriptingCommands) {
        return scriptMapSha.compute(scriptName, (key, result) -> {
            if (log.isInfoEnabled()) {
                log.info("reloadScript - scriptName : [{}].", scriptName);
            }
            return tryGetSha(scriptName, scriptingCommands);
        });
    }

    public static <T> Mono<T> doEnsureScript(String scriptName, RedisScriptingReactiveCommands<String, String> scriptingCommands, Function<String, Mono<T>> doSha) {
        return loadScript(scriptName, scriptingCommands)
                .flatMap(doSha)
                .onErrorResume(throwable -> Exceptions.unwrap(throwable) instanceof RedisNoScriptException, (throwable -> {
                    if (log.isWarnEnabled()) {
                        log.warn("Actively reloading script[{}]:[{}]", scriptName, throwable.getMessage());
                    }
                    return reloadScript(scriptName, scriptingCommands).flatMap(doSha);
                }));
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
    public static Mono<Void> clearTestData(String namespace, RedisScriptingReactiveCommands<String, String> scriptingCommands) {
        if (log.isWarnEnabled()) {
            log.warn("clearTestData - namespace : [{}].", namespace);
        }
        return RedisScripts.loadScript(WARN_CLEAR_TEST_DATA, scriptingCommands)
                .flatMap(sha -> scriptingCommands.evalsha(sha, ScriptOutputType.STATUS, new String[]{namespace}).then()
                );
    }

}
