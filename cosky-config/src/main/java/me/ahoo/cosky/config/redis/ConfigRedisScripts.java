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

package me.ahoo.cosky.config.redis;

import io.lettuce.core.api.reactive.RedisScriptingReactiveCommands;
import me.ahoo.cosky.core.redis.RedisScripts;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * @author ahoo wang
 */
public final class ConfigRedisScripts {
    public static final String CONFIG_SET = "config_set.lua";
    public static final String CONFIG_REMOVE = "config_remove.lua";
    public static final String CONFIG_ROLLBACK = "config_rollback.lua";

    public static <T> Mono<T> doConfigSet(RedisScriptingReactiveCommands<String, String> scriptingCommands, Function<String, Mono<T>> doSha) {
        return RedisScripts.doEnsureScript(CONFIG_SET, scriptingCommands, doSha);
    }
    public static <T> Mono<T> doConfigRemove(RedisScriptingReactiveCommands<String, String> scriptingCommands, Function<String, Mono<T>> doSha) {
        return RedisScripts.doEnsureScript(CONFIG_REMOVE, scriptingCommands, doSha);
    }
    public static <T> Mono<T> doConfigRollback(RedisScriptingReactiveCommands<String, String> scriptingCommands, Function<String, Mono<T>> doSha) {
        return RedisScripts.doEnsureScript(CONFIG_ROLLBACK, scriptingCommands, doSha);
    }

}
