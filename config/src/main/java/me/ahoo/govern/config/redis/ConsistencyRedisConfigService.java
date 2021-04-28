package me.ahoo.govern.config.redis;

import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.govern.config.*;
import me.ahoo.govern.core.NamespacedContext;
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
    private final ConfigListener configListener;

    private final ConcurrentHashMap<NamespacedConfigId, CompletableFuture<Config>> configMap;
    private final ConcurrentHashMap<NamespacedConfigId, ConfigChangedListener> configMapListener;

    public ConsistencyRedisConfigService(ConfigService delegate, MessageListenable messageListenable) {
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
    public CompletableFuture<Set<String>> getConfigs(String namespace) {
        return delegate.getConfigs(namespace);
    }

    @Override
    public CompletableFuture<Config> getConfig(String configId) {
        return getConfig(NamespacedContext.GLOBAL.getNamespace(), configId);
    }

    @Override
    public CompletableFuture<Config> getConfig(String namespace, String configId) {
        return configMap.computeIfAbsent(NamespacedConfigId.of(namespace, configId), (_configId) -> addListener(namespace, configId).
                thenCompose(nil -> delegate.getConfig(namespace, configId)));
    }

    private CompletableFuture<Void> addListener(String namespace, String configId) {
        var topicStr = RedisKeySpaces.getTopicOfKey(ConfigKeyGenerator.getConfigKey(namespace, configId));
        var configTopic = ChannelTopic.of(topicStr);
        return messageListenable.addListener(configTopic, configListener);
    }

    @Override
    public CompletableFuture<Boolean> setConfig(String configId, String data) {
        return delegate.setConfig(configId, data);
    }

    @Override
    public CompletableFuture<Boolean> setConfig(String namespace, String configId, String data) {
        return delegate.setConfig(namespace, configId, data);
    }

    @Override
    public CompletableFuture<Boolean> removeConfig(String configId) {
        return delegate.removeConfig(configId);
    }

    @Override
    public CompletableFuture<Boolean> removeConfig(String namespace, String configId) {
        return delegate.removeConfig(namespace, configId);
    }

    @Override
    public CompletableFuture<Boolean> addListener(String configId, ConfigChangedListener configChangedListener) {
        return addListener(NamespacedContext.GLOBAL.getNamespace(), configId, configChangedListener);
    }

    @Override
    public CompletableFuture<Boolean> addListener(String namespace, String configId, ConfigChangedListener configChangedListener) {
        var putOk = configMapListener.putIfAbsent(NamespacedConfigId.of(namespace, configId), configChangedListener) == null;
        return CompletableFuture.completedFuture(putOk);
    }

    @Override
    public CompletableFuture<Boolean> removeListener(String configId) {
        return removeListener(NamespacedContext.GLOBAL.getNamespace(), configId);
    }

    @Override
    public CompletableFuture<Boolean> removeListener(String namespace, String configId) {
        var removeOk = configMapListener.remove(NamespacedConfigId.of(namespace, configId)) != null;
        return CompletableFuture.completedFuture(removeOk);
    }

    @Override
    public CompletableFuture<Boolean> rollback(String configId, int targetVersion) {
        return delegate.rollback(configId, targetVersion);
    }

    @Override
    public CompletableFuture<Boolean> rollback(String namespace, String configId, int targetVersion) {
        return delegate.rollback(namespace, configId, targetVersion);
    }

    @Override
    public CompletableFuture<List<ConfigVersion>> getConfigVersions(String configId) {
        return delegate.getConfigVersions(configId);
    }

    @Override
    public CompletableFuture<List<ConfigVersion>> getConfigVersions(String namespace, String configId) {
        return delegate.getConfigVersions(namespace, configId);
    }

    @Override
    public CompletableFuture<ConfigHistory> getConfigHistory(String configId, int version) {
        return delegate.getConfigHistory(configId, version);
    }

    @Override
    public CompletableFuture<ConfigHistory> getConfigHistory(String namespace, String configId, int version) {
        return delegate.getConfigHistory(namespace, configId, version);
    }


    private class ConfigListener implements MessageListener {
//        private final static String RENAME_FROM = "rename_from";

        @Override
        public void onMessage(Topic topic, String channel, String message) {
            if (log.isInfoEnabled()) {
                log.info("onMessage@ConfigListener - topic:[{}] - channel:[{}] - message:[{}]", topic, channel, message);
            }

            /**
             * 忽略部分消息
             */
            var key = RedisKeySpaces.getKeyOfChannel(channel);
            var namespacedConfigId = ConfigKeyGenerator.getConfigIdOfKey(key);
            configMap.put(namespacedConfigId, delegate.getConfig(namespacedConfigId.getNamespace(), namespacedConfigId.getConfigId()));
            var configChangedListener = configMapListener.get(namespacedConfigId);
            if (Objects.isNull(configChangedListener)) {
                return;
            }
            configChangedListener.onChange(namespacedConfigId, message);
        }
    }
}
