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
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.hash.Hashing;
import io.lettuce.core.KeyValue;
import io.lettuce.core.ScriptOutputType;

import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosky.config.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author ahoo wang
 */
@Slf4j
public class RedisConfigService implements ConfigService {

    private final RedisClusterReactiveCommands<String, String> redisCommands;

    public RedisConfigService(RedisClusterReactiveCommands<String, String> redisCommands) {
        this.redisCommands = redisCommands;
    }

    @Override
    public Mono<Set<String>> getConfigs(String namespace) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");

        if (log.isDebugEnabled()) {
            log.debug("getConfigs  @ namespace:[{}].", namespace);
        }
        String configIdxKey = ConfigKeyGenerator.getConfigIdxKey(namespace);
        return redisCommands.smembers(configIdxKey)
                .map(configKey -> ConfigKeyGenerator.getConfigIdOfKey(configKey).getConfigId())
                .collect(Collectors.toSet());
    }


    @Override
    public Mono<Config> getConfig(String namespace, String configId) {
        ensureNamespacedConfigId(namespace, configId);

        if (log.isDebugEnabled()) {
            log.debug("getConfig - configId:[{}]  @ namespace:[{}].", configId, namespace);
        }
        String configKey = ConfigKeyGenerator.getConfigKey(namespace, configId);
        return getAndDecodeConfig(configKey, ConfigCodec::decode);
    }

    private void ensureNamespacedConfigId(String namespace, String configId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(configId), "configId can not be empty!");
    }

    /**
     * @param configId
     * @param data
     * @return
     */
    @Override
    public Mono<Boolean> setConfig(String namespace, String configId, String data) {
        ensureNamespacedConfigId(namespace, configId);

        String hash = Hashing.sha256().hashString(data, Charsets.UTF_8).toString();
        if (log.isInfoEnabled()) {
            log.info("setConfig - configId:[{}] - hash:[{}]  @ namespace:[{}].", configId, hash, namespace);
        }

        return ConfigRedisScripts.doConfigSet(redisCommands, sha -> {
            String[] keys = {namespace};
            String[] values = {configId, data, hash};

            return redisCommands.evalsha(sha, ScriptOutputType.BOOLEAN, keys, values)
                    .cast(Boolean.class)
                    .next();
        });
    }

    @Override
    public Mono<Boolean> removeConfig(String namespace, String configId) {
        ensureNamespacedConfigId(namespace, configId);

        if (log.isInfoEnabled()) {
            log.info("removeConfig - configId:[{}] @ namespace:[{}].", configId, namespace);
        }

        return ConfigRedisScripts.doConfigRemove(redisCommands, sha -> {
            String[] keys = {namespace};
            String[] values = {configId};
            return redisCommands.evalsha(sha, ScriptOutputType.BOOLEAN, keys, values)
                    .cast(Boolean.class)
                    .next();
        });
    }

    @Override
    public Mono<Boolean> containsConfig(String namespace, String configId) {
        ensureNamespacedConfigId(namespace, configId);

        String configKey = ConfigKeyGenerator.getConfigKey(namespace, configId);
        return redisCommands.exists(configKey).map(count -> count > 0);
    }

    @Override
    public Mono<Boolean> rollback(String namespace, String configId, int targetVersion) {
        ensureNamespacedConfigId(namespace, configId);

        if (log.isInfoEnabled()) {
            log.info("rollback - configId:[{}] - targetVersion:[{}]  @ namespace:[{}].", configId, targetVersion, namespace);
        }
        return ConfigRedisScripts.doConfigRollback(redisCommands, sha -> {
            String[] keys = {namespace};
            String[] values = {configId, String.valueOf(targetVersion)};
            return redisCommands.evalsha(sha, ScriptOutputType.BOOLEAN, keys, values)
                    .cast(Boolean.class)
                    .next();
        });
    }

    private final static int HISTORY_STOP = HISTORY_SIZE - 1;

    @Override
    public Mono<List<ConfigVersion>> getConfigVersions(String namespace, String configId) {
        ensureNamespacedConfigId(namespace, configId);

        String configHistoryIdxKey = ConfigKeyGenerator.getConfigHistoryIdxKey(namespace, configId);
        return redisCommands.zrevrange(configHistoryIdxKey, 0, HISTORY_STOP)
                .map(configHistoryKey ->
                        ConfigKeyGenerator.getConfigVersionOfHistoryKey(namespace, configHistoryKey))
                .collect(Collectors.toList());
    }

    @Override
    public Mono<ConfigHistory> getConfigHistory(String namespace, String configId, int version) {
        ensureNamespacedConfigId(namespace, configId);

        String configHistoryKey = ConfigKeyGenerator.getConfigHistoryKey(namespace, configId, version);
        return getAndDecodeConfig(configHistoryKey, ConfigCodec::decodeHistory);
    }

    private <T extends Config> Mono<T> getAndDecodeConfig(String configHistoryKey, Function<Map<String, String>, T> decodeFun) {
        return redisCommands.hgetall(configHistoryKey)
                .collectMap(KeyValue::getKey, KeyValue::getValue, HashMap::new)
                .mapNotNull((map) -> {
                    if (map.isEmpty()) {
                        return null;
                    }
                    return decodeFun.apply(map);
                });
    }

}
