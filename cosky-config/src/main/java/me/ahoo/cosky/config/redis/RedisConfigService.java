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

import me.ahoo.cosky.config.Config;
import me.ahoo.cosky.config.ConfigCodec;
import me.ahoo.cosky.config.ConfigHistory;
import me.ahoo.cosky.config.ConfigKeyGenerator;
import me.ahoo.cosky.config.ConfigService;
import me.ahoo.cosky.config.ConfigVersion;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Redis Config Service.
 *
 * @author ahoo wang
 */
@Slf4j
public class RedisConfigService implements ConfigService {
    private final ReactiveStringRedisTemplate redisTemplate;
    
    public RedisConfigService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @Override
    public Flux<String> getConfigs(String namespace) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        if (log.isDebugEnabled()) {
            log.debug("getConfigs  @ namespace:[{}].", namespace);
        }
        
        String configIdxKey = ConfigKeyGenerator.getConfigIdxKey(namespace);
    
        return redisTemplate.opsForSet().members(configIdxKey)
            .map(configKey -> ConfigKeyGenerator.getConfigIdOfKey(configKey).getConfigId());
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
    
    @Override
    public Mono<Boolean> setConfig(String namespace, String configId, String data) {
        ensureNamespacedConfigId(namespace, configId);
    
        String hash = Hashing.sha256().hashString(data, Charsets.UTF_8).toString();
        if (log.isInfoEnabled()) {
            log.info("setConfig - configId:[{}] - hash:[{}]  @ namespace:[{}].", configId, hash, namespace);
        }
        return redisTemplate.execute(
                ConfigRedisScripts.SCRIPT_CONFIG_SET,
                Collections.singletonList(namespace),
                Lists.newArrayList(configId, data, hash)
            )
            .next();
    }
    
    @Override
    public Mono<Boolean> removeConfig(String namespace, String configId) {
        ensureNamespacedConfigId(namespace, configId);
    
        if (log.isInfoEnabled()) {
            log.info("removeConfig - configId:[{}] @ namespace:[{}].", configId, namespace);
        }
    
        return redisTemplate.execute(
                ConfigRedisScripts.SCRIPT_CONFIG_REMOVE,
                Collections.singletonList(namespace),
                Collections.singletonList(configId)
            )
            .next();
    }
    
    @Override
    public Mono<Boolean> containsConfig(String namespace, String configId) {
        ensureNamespacedConfigId(namespace, configId);
    
        String configKey = ConfigKeyGenerator.getConfigKey(namespace, configId);
        return redisTemplate.hasKey(configKey);
    }
    
    @Override
    public Mono<Boolean> rollback(String namespace, String configId, int targetVersion) {
        ensureNamespacedConfigId(namespace, configId);
    
        if (log.isInfoEnabled()) {
            log.info("rollback - configId:[{}] - targetVersion:[{}]  @ namespace:[{}].", configId, targetVersion, namespace);
        }
        return redisTemplate.execute(
                ConfigRedisScripts.SCRIPT_CONFIG_ROLLBACK,
                Collections.singletonList(namespace),
                Lists.newArrayList(configId, String.valueOf(targetVersion))
            )
            .next();
    }
    
    private static final long HISTORY_STOP = HISTORY_SIZE - 1;
    
    @Override
    public Flux<ConfigVersion> getConfigVersions(String namespace, String configId) {
        ensureNamespacedConfigId(namespace, configId);
    
        String configHistoryIdxKey = ConfigKeyGenerator.getConfigHistoryIdxKey(namespace, configId);
        return redisTemplate
            .opsForZSet()
            .reverseRange(
                configHistoryIdxKey,
                org.springframework.data.domain.Range.closed(0L, HISTORY_STOP)
            )
            .map(configHistoryKey ->
                ConfigKeyGenerator.getConfigVersionOfHistoryKey(namespace, configHistoryKey));
    }
    
    @Override
    public Mono<ConfigHistory> getConfigHistory(String namespace, String configId, int version) {
        ensureNamespacedConfigId(namespace, configId);
        String configHistoryKey = ConfigKeyGenerator.getConfigHistoryKey(namespace, configId, version);
        return getAndDecodeConfig(configHistoryKey, ConfigCodec::decodeHistory);
    }
    
    private <T extends Config> Mono<T> getAndDecodeConfig(String configHistoryKey, Function<Map<String, String>, T> decodeFun) {
        return redisTemplate
            .<String, String>opsForHash()
            .entries(configHistoryKey)
            .collectMap(Map.Entry::getKey, Map.Entry::getValue, HashMap::new)
            .mapNotNull((map) -> {
                if (map.isEmpty()) {
                    return null;
                }
                return decodeFun.apply(map);
            });
    }
    
}
