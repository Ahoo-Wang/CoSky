package me.ahoo.govern.config.redis;

import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.govern.config.*;
import me.ahoo.govern.core.listener.ChannelTopic;
import me.ahoo.govern.core.listener.MessageListenable;
import me.ahoo.govern.core.listener.MessageListener;
import me.ahoo.govern.core.listener.Topic;
import me.ahoo.govern.core.util.RedisKeySpaces;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author ahoo wang
 */
@Slf4j
public class ConsistencyRedisConfigService implements ConfigService, ConfigListenable {
    private final ConfigService delegate;
    private final MessageListenable messageListenable;
    private final ConfigKeyGenerator keyGenerator;
    private final ConfigListener configListener;

    private final ConcurrentHashMap<String, CompletableFuture<Config>> configMap;
    private final ConcurrentHashMap<String, ConfigChangedListener> configMapListener;

    public ConsistencyRedisConfigService(ConfigKeyGenerator keyGenerator, ConfigService delegate, MessageListenable messageListenable) {
        this.keyGenerator = keyGenerator;
        this.configMap = new ConcurrentHashMap<>();
        this.configMapListener = new ConcurrentHashMap<>();
        this.delegate = delegate;
        this.messageListenable = messageListenable;
        this.configListener = new ConfigListener();
    }

    @Override
    public CompletableFuture<Set<String>> getConfigs() {
        return delegate.getConfigs();
    }

    @Override
    public CompletableFuture<Config> getConfig(String configId) {
        return configMap.computeIfAbsent(configId, (_configId) -> addListener(configId).
                thenCompose(nil -> delegate.getConfig(configId)));
    }

    private CompletableFuture<Void> addListener(String configId) {
        var topicStr = RedisKeySpaces.getTopicOfKey(keyGenerator.getConfigKey(configId));
        var configTopic = ChannelTopic.of(topicStr);
        return messageListenable.addListener(configTopic, configListener);
    }

    @Override
    public CompletableFuture<Boolean> setConfig(String configId, String data) {
        return delegate.setConfig(configId, data);
    }

    @Override
    public CompletableFuture<Boolean> removeConfig(String configId) {
        return delegate.removeConfig(configId);
    }

    @Override
    public CompletableFuture<Boolean> addListener(String configId, ConfigChangedListener configChangedListener) {
        var putOk = configMapListener.putIfAbsent(configId, configChangedListener) == null;
        return CompletableFuture.completedFuture(putOk);
    }

    @Override
    public CompletableFuture<Boolean> removeListener(String configId) {
        var removeOk = configMapListener.remove(configId) != null;
        return CompletableFuture.completedFuture(removeOk);
    }

    @Override
    public CompletableFuture<Boolean> rollback(String configId, int targetVersion) {
        return delegate.rollback(configId, targetVersion);
    }

    @Override
    public CompletableFuture<List<ConfigVersion>> getConfigVersions(String configId) {
        return delegate.getConfigVersions(configId);
    }

    @Override
    public CompletableFuture<ConfigHistory> getConfigHistory(String configId, int version) {
        return delegate.getConfigHistory(configId, version);
    }

    private class ConfigListener implements MessageListener {
//        private final static String RENAME_FROM = "rename_from";

        @Override
        public void onMessage(Topic topic, String channel, String message) {
            if (log.isInfoEnabled()){
                log.info("onMessage@ConfigListener - topic:[{}] - channel:[{}] - message:[{}]", topic, channel, message);
            }

            /**
             * 忽略部分消息
             */
            var key = RedisKeySpaces.getKeyOfChannel(channel);
            var configId = keyGenerator.getConfigIdOfKey(key);
            configMap.put(configId, delegate.getConfig(configId));
            var configChangedListener = configMapListener.get(configId);
            if (Objects.isNull(configChangedListener)) {
                return;
            }
            configChangedListener.onChange(configId, message);
        }
    }
}
