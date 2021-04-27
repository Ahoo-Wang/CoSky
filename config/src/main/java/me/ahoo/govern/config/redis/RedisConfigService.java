package me.ahoo.govern.config.redis;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.hash.Hashing;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.govern.config.*;
import me.ahoo.govern.core.Namespaced;

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
    private final ConfigKeyGenerator keyGenerator;
    private final RedisClusterAsyncCommands<String, String> redisCommands;

    public RedisConfigService(ConfigKeyGenerator keyGenerator,
                              RedisClusterAsyncCommands<String, String> redisCommands) {
        this.keyGenerator = keyGenerator;
        this.redisCommands = redisCommands;
    }

    @Override
    public CompletableFuture<Set<String>> getConfigs() {
        if (log.isInfoEnabled()) {
            log.info("getConfigs .");
        }
        var configIdxKey = keyGenerator.getConfigIdxKey();
        return redisCommands.smembers(configIdxKey).thenApply(configKeySet ->
                configKeySet.stream()
                        .map(configKey -> keyGenerator.getConfigIdOfKey(configKey)
                        ).collect(Collectors.toSet())).toCompletableFuture();
    }

    @Override
    public CompletableFuture<Config> getConfig(String configId) {
        if (log.isInfoEnabled()) {
            log.info("getConfig - configId:[{}] .", configId);
        }
        var configKey = keyGenerator.getConfigKey(configId);
        return getAndDecodeConfig(configKey, ConfigCodec::decode);
    }

    /**
     * TODO 禁止添加已删除的KEY？
     *
     * @param configId
     * @param data
     * @return
     */
    @Override
    public CompletableFuture<Boolean> setConfig(String configId, String data) {
        String hash = Hashing.sha256().hashString(data, Charsets.UTF_8).toString();
        if (log.isInfoEnabled()) {
            log.info("setConfig - configId:[{}] - hash:[{}] .", configId, hash);
        }

        return ConfigRedisScripts.loadConfigSet(redisCommands)
                .thenCompose(sha -> {
                    String[] keys = {keyGenerator.getNamespace(), configId, data, hash};
                    RedisFuture<Boolean> redisFuture = redisCommands.evalsha(sha, ScriptOutputType.BOOLEAN, keys);
                    return redisFuture;
                });
    }

    @Override
    public CompletableFuture<Boolean> removeConfig(String configId) {
        if (log.isInfoEnabled()) {
            log.info("removeConfig - configId:[{}] .", configId);
        }

        return ConfigRedisScripts.loadConfigRemove(redisCommands)
                .thenCompose(sha -> {
                    String[] keys = {keyGenerator.getNamespace(), configId};
                    RedisFuture<Boolean> redisFuture = redisCommands.evalsha(sha, ScriptOutputType.BOOLEAN, keys);
                    return redisFuture;
                });
    }

    @Override
    public CompletableFuture<Boolean> rollback(String configId, int targetVersion) {
        if (log.isInfoEnabled()) {
            log.info("rollback - configId:[{}] - targetVersion:[{}] .", configId, targetVersion);
        }

        return ConfigRedisScripts.loadConfigRollback(redisCommands)
                .thenCompose(sha -> {
                    String[] keys = {keyGenerator.getNamespace(), configId, String.valueOf(targetVersion)};
                    RedisFuture<Boolean> redisFuture = redisCommands.evalsha(sha, ScriptOutputType.BOOLEAN, keys);
                    return redisFuture;
                });
    }

    private final static int HISTORY_STOP = HISTORY_SIZE - 1;

    @Override
    public CompletableFuture<List<ConfigVersion>> getConfigVersions(String configId) {
        var configHistoryIdxKey = keyGenerator.getConfigHistoryIdxKey(configId);
        return redisCommands.zrevrange(configHistoryIdxKey, 0, HISTORY_STOP)
                .thenApply(configHistoryKeyList ->
                        configHistoryKeyList.stream()
                                .map(configHistoryKey ->
                                        keyGenerator.getConfigVersionOfHistoryKey(configHistoryKey))
                                .collect(Collectors.toList()))
                .toCompletableFuture();
    }

    @Override
    public CompletableFuture<ConfigHistory> getConfigHistory(String configId, int version) {
        var configHistoryKey = keyGenerator.getConfigHistoryKey(configId, version);
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

    @Override
    public String getNamespace() {
        return keyGenerator.getNamespace();
    }
}
