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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosky.config.*;
import me.ahoo.cosky.core.listener.MessageListenable;
import me.ahoo.cosky.core.listener.MessageListener;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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

    private final ConcurrentHashMap<NamespacedConfigId, Mono<Config>> configMap;
    private final ConcurrentHashMap<NamespacedConfigId, CopyOnWriteArraySet<ConfigChangedListener>> configMapListener;

    public ConsistencyRedisConfigService(ConfigService delegate, MessageListenable messageListenable) {
        this.configMap = new ConcurrentHashMap<>();
        this.configMapListener = new ConcurrentHashMap<>();
        this.delegate = delegate;
        this.messageListenable = messageListenable;
        this.configListener = new ConfigListener();
    }

    @Override
    public Mono<Set<String>> getConfigs(String namespace) {
        return delegate.getConfigs(namespace);
    }

    @Override
    public Mono<Config> getConfig(String namespace, String configId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(configId), "configId can not be empty!");

        return configMap.computeIfAbsent(NamespacedConfigId.of(namespace, configId), (_configId) ->
                {
                    addListener(namespace, configId);
                    return delegate.getConfig(namespace, configId).cache();
                }
        );
    }

    private void addListener(String namespace, String configId) {
        String topicStr = ConfigKeyGenerator.getConfigKey(namespace, configId);
        messageListenable.addChannelListener(topicStr, configListener);
    }


    @Override
    public Mono<Boolean> setConfig(String namespace, String configId, String data) {
        return delegate.setConfig(namespace, configId, data);
    }

    @Override
    public Mono<Boolean> removeConfig(String configId) {
        return delegate.removeConfig(configId);
    }

    @Override
    public Mono<Boolean> removeConfig(String namespace, String configId) {
        return delegate.removeConfig(namespace, configId);
    }

    @Override
    public Mono<Boolean> containsConfig(String namespace, String configId) {
        return delegate.containsConfig(namespace, configId);
    }

    @Override
    public void addListener(NamespacedConfigId namespacedConfigId, ConfigChangedListener configChangedListener) {
        configMapListener.compute(namespacedConfigId, (key, val) -> {
            CopyOnWriteArraySet<ConfigChangedListener> listeners = val;
            if (Objects.isNull(val)) {
                addListener(namespacedConfigId.getNamespace(), namespacedConfigId.getConfigId());
                listeners = new CopyOnWriteArraySet<>();
            }
            listeners.add(configChangedListener);
            return listeners;
        });
    }

    @Override
    public void removeListener(NamespacedConfigId namespacedConfigId, ConfigChangedListener configChangedListener) {
        configMapListener.compute(namespacedConfigId, (key, val) -> {
            if (Objects.isNull(val)) {
                return null;
            }
            val.remove(configChangedListener);
            if (val.isEmpty()) {
                return null;
            }
            return val;
        });
    }

    @Override
    public Mono<Boolean> rollback(String configId, int targetVersion) {
        return delegate.rollback(configId, targetVersion);
    }

    @Override
    public Mono<Boolean> rollback(String namespace, String configId, int targetVersion) {
        return delegate.rollback(namespace, configId, targetVersion);
    }

    @Override
    public Mono<List<ConfigVersion>> getConfigVersions(String namespace, String configId) {
        return delegate.getConfigVersions(namespace, configId);
    }

    @Override
    public Mono<ConfigHistory> getConfigHistory(String namespace, String configId, int version) {
        return delegate.getConfigHistory(namespace, configId, version);
    }


    private class ConfigListener implements MessageListener {

        @Override
        public void onMessage(@Nullable String pattern, String channel, String message) {
            if (log.isInfoEnabled()) {
                log.info("onMessage@ConfigListener - pattern:[{}] - channel:[{}] - message:[{}]", pattern, channel, message);
            }

            final String configkey = channel;
            NamespacedConfigId namespacedConfigId = ConfigKeyGenerator.getConfigIdOfKey(configkey);
            configMap.put(namespacedConfigId, delegate.getConfig(namespacedConfigId.getNamespace(), namespacedConfigId.getConfigId()).cache());
            CopyOnWriteArraySet<ConfigChangedListener> configChangedListeners = configMapListener.get(namespacedConfigId);
            if (Objects.isNull(configChangedListeners) || configChangedListeners.isEmpty()) {
                return;
            }
            configChangedListeners.forEach(configChangedListener -> configChangedListener.onChange(namespacedConfigId, message));
        }
    }
}
