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

package me.ahoo.cosky.config.redis;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.hash.Hashing;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.cosky.config.*;
import me.ahoo.cosky.core.NamespacedContext;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * @author ahoo wang
 */
@Slf4j
public class RedisConfigService implements ConfigService {

    private final RedisClusterAsyncCommands<String, String> redisCommands;

    public RedisConfigService(RedisClusterAsyncCommands<String, String> redisCommands) {
        this.redisCommands = redisCommands;
    }

    @Override
    public CompletableFuture<Set<String>> getConfigs() {
        return getConfigs(NamespacedContext.GLOBAL.getNamespace());
    }

    @Override
    public CompletableFuture<Set<String>> getConfigs(String namespace) {
        if (log.isDebugEnabled()) {
            log.debug("getConfigs  @ namespace:[{}].", namespace);
        }
        var configIdxKey = ConfigKeyGenerator.getConfigIdxKey(namespace);
        return redisCommands.smembers(configIdxKey).thenApply(configKeySet ->
                configKeySet.stream()
                        .map(configKey -> ConfigKeyGenerator.getConfigIdOfKey(configKey).getConfigId()
                        ).collect(Collectors.toSet())).toCompletableFuture();
    }

    @Override
    public CompletableFuture<Config> getConfig(String configId) {
        return getConfig(NamespacedContext.GLOBAL.getNamespace(), configId);
    }

    @Override
    public CompletableFuture<Config> getConfig(String namespace, String configId) {
        if (log.isDebugEnabled()) {
            log.debug("getConfig - configId:[{}]  @ namespace:[{}].", configId, namespace);
        }
        var configKey = ConfigKeyGenerator.getConfigKey(namespace, configId);
        return getAndDecodeConfig(configKey, ConfigCodec::decode);
    }

    /**
     * @param configId
     * @param data
     * @return
     */
    @Override
    public CompletableFuture<Boolean> setConfig(String configId, String data) {
        return setConfig(NamespacedContext.GLOBAL.getNamespace(), configId, data);
    }

    @Override
    public CompletableFuture<Boolean> setConfig(String namespace, String configId, String data) {
        String hash = Hashing.sha256().hashString(data, Charsets.UTF_8).toString();
        if (log.isInfoEnabled()) {
            log.info("setConfig - configId:[{}] - hash:[{}]  @ namespace:[{}].", configId, hash, namespace);
        }
        return ConfigRedisScripts.doConfigSet(redisCommands, sha -> {
            String[] keys = {namespace};
            String[] values = {configId, data, hash};
            return redisCommands.evalsha(sha, ScriptOutputType.BOOLEAN, keys, values);
        });
    }

    @Override
    public CompletableFuture<Boolean> removeConfig(String configId) {
        return removeConfig(NamespacedContext.GLOBAL.getNamespace(), configId);
    }

    @Override
    public CompletableFuture<Boolean> removeConfig(String namespace, String configId) {
        if (log.isInfoEnabled()) {
            log.info("removeConfig - configId:[{}] @ namespace:[{}].", configId, namespace);
        }
        return ConfigRedisScripts.doConfigRemove(redisCommands, sha -> {
            String[] keys = {namespace};
            String[] values = {configId};
            return redisCommands.evalsha(sha, ScriptOutputType.BOOLEAN, keys, values);
        });
    }

    @Override
    public CompletableFuture<Boolean> containsConfig(String namespace, String configId) {
        var configKey = ConfigKeyGenerator.getConfigKey(namespace, configId);
        return redisCommands.exists(configKey).thenApply(count -> count > 0).toCompletableFuture();
    }

    @Override
    public CompletableFuture<Boolean> rollback(String configId, int targetVersion) {
        return rollback(NamespacedContext.GLOBAL.getNamespace(), configId, targetVersion);
    }

    @Override
    public CompletableFuture<Boolean> rollback(String namespace, String configId, int targetVersion) {
        if (log.isInfoEnabled()) {
            log.info("rollback - configId:[{}] - targetVersion:[{}]  @ namespace:[{}].", configId, targetVersion, namespace);
        }
        return ConfigRedisScripts.doConfigRollback(redisCommands, sha -> {
            String[] keys = {namespace};
            String[] values = {configId, String.valueOf(targetVersion)};
            return redisCommands.evalsha(sha, ScriptOutputType.BOOLEAN, keys, values);
        });
    }

    private final static int HISTORY_STOP = HISTORY_SIZE - 1;

    @Override
    public CompletableFuture<List<ConfigVersion>> getConfigVersions(String configId) {
        return getConfigVersions(NamespacedContext.GLOBAL.getNamespace(), configId);
    }

    @Override
    public CompletableFuture<List<ConfigVersion>> getConfigVersions(String namespace, String configId) {
        var configHistoryIdxKey = ConfigKeyGenerator.getConfigHistoryIdxKey(namespace, configId);
        return redisCommands.zrevrange(configHistoryIdxKey, 0, HISTORY_STOP)
                .thenApply(configHistoryKeyList ->
                        configHistoryKeyList.stream()
                                .map(configHistoryKey ->
                                        ConfigKeyGenerator.getConfigVersionOfHistoryKey(namespace, configHistoryKey))
                                .collect(Collectors.toList()))
                .toCompletableFuture();
    }

    @Override
    public CompletableFuture<ConfigHistory> getConfigHistory(String configId, int version) {
        return getConfigHistory(NamespacedContext.GLOBAL.getNamespace(), configId, version);
    }

    @Override
    public CompletableFuture<ConfigHistory> getConfigHistory(String namespace, String configId, int version) {
        var configHistoryKey = ConfigKeyGenerator.getConfigHistoryKey(namespace, configId, version);
        return getAndDecodeConfig(configHistoryKey, ConfigCodec::decodeHistory);
    }

    private <T extends Config> CompletableFuture<T> getAndDecodeConfig(String configHistoryKey, Function<Map<String, String>, T> decodeFun) {
        return redisCommands.hgetall(configHistoryKey).
                thenApply(configData -> {
                    if (configData.isEmpty()) {
                        return null;
                    }
                    return decodeFun.apply(configData);
                })
                .toCompletableFuture();
    }

}
