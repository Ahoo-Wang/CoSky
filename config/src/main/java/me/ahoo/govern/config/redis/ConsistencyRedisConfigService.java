package me.ahoo.govern.config.redis;

import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.govern.config.*;
import me.ahoo.govern.core.NamespacedContext;
import me.ahoo.govern.core.listener.ChannelTopic;
import me.ahoo.govern.core.listener.MessageListenable;
import me.ahoo.govern.core.listener.MessageListener;
import me.ahoo.govern.core.listener.Topic;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * @author ahoo wang
 */
@Slf4j
public class ConsistencyRedisConfigService implements ConfigService, ConfigListenable {
    private final ConfigService delegate;
    private final MessageListenable messageListenable;
    private final ConfigListener configListener;

    private final ConcurrentHashMap<NamespacedConfigId, CompletableFuture<Config>> configMap;
    private final ConcurrentHashMap<NamespacedConfigId, CopyOnWriteArraySet<ConfigChangedListener>> configMapListener;

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
        var topicStr = ConfigKeyGenerator.getConfigKey(namespace, configId);
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
    public CompletableFuture<Boolean> addListener(NamespacedConfigId namespacedConfigId, ConfigChangedListener configChangedListener) {
        configMapListener.compute(namespacedConfigId, (key, val) -> {
            CopyOnWriteArraySet<ConfigChangedListener> listeners = val;
            if (Objects.isNull(val)) {
                listeners = new CopyOnWriteArraySet<>();
            }
            listeners.add(configChangedListener);
            return listeners;
        });
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Boolean> removeListener(NamespacedConfigId namespacedConfigId, ConfigChangedListener configChangedListener) {
        configMapListener.compute(namespacedConfigId, (key, val) -> {
            if (Objects.isNull(val)) {
                return null;
            }
            CopyOnWriteArraySet<ConfigChangedListener> listeners = val;
            listeners.remove(configChangedListener);
            if (listeners.isEmpty()) {
                return null;
            }
            return listeners;
        });

        return CompletableFuture.completedFuture(true);
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

        @Override
        public void onMessage(Topic topic, String channel, String message) {
            if (log.isInfoEnabled()) {
                log.info("onMessage@ConfigListener - topic:[{}] - channel:[{}] - message:[{}]", topic, channel, message);
            }

            final var configkey = channel;
            var namespacedConfigId = ConfigKeyGenerator.getConfigIdOfKey(configkey);
            configMap.put(namespacedConfigId, delegate.getConfig(namespacedConfigId.getNamespace(), namespacedConfigId.getConfigId()));
            var configChangedListeners = configMapListener.get(namespacedConfigId);
            if (Objects.isNull(configChangedListeners) || configChangedListeners.isEmpty()) {
                return;
            }
            configChangedListeners.forEach(configChangedListener -> configChangedListener.onChange(namespacedConfigId, message));
        }
    }
}
