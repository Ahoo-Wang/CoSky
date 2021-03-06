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
import me.ahoo.cosky.config.ConfigChangedEvent;
import me.ahoo.cosky.config.ConfigHistory;
import me.ahoo.cosky.config.ConfigKeyGenerator;
import me.ahoo.cosky.config.ListenableConfigService;
import me.ahoo.cosky.config.ConfigService;
import me.ahoo.cosky.config.ConfigVersion;
import me.ahoo.cosky.config.NamespacedConfigId;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Consistency Redis Config Service.
 *
 * @author ahoo wang
 */
@Slf4j
public class ConsistencyRedisConfigService implements ListenableConfigService {
    private final ConfigService delegate;
    private final ReactiveRedisMessageListenerContainer listenerContainer;
    private final ConcurrentHashMap<NamespacedConfigId, Mono<Config>> configMapCache;
    
    @VisibleForTesting
    @Nullable
    private final Consumer<ConfigChangedEvent> hookOnResetCache;
    
    public ConsistencyRedisConfigService(ConfigService delegate,
                                         ReactiveRedisMessageListenerContainer listenerContainer
    ) {
        this(delegate, listenerContainer, null);
    }
    
    public ConsistencyRedisConfigService(ConfigService delegate,
                                         ReactiveRedisMessageListenerContainer listenerContainer,
                                         Consumer<ConfigChangedEvent> hookOnResetCache) {
        this.listenerContainer = listenerContainer;
        this.configMapCache = new ConcurrentHashMap<>();
        this.delegate = delegate;
        this.hookOnResetCache = hookOnResetCache;
    }
    
    @Override
    public Flux<ConfigChangedEvent> listen(NamespacedConfigId topic) {
        return listen(topic.getNamespace(), topic.getConfigId());
    }
    
    public Flux<ConfigChangedEvent> listen(String namespace, String configId) {
        String topicStr = ConfigKeyGenerator.getConfigKey(namespace, configId);
        return listenerContainer
            .receive(ChannelTopic.of(topicStr))
            .map(message -> {
                NamespacedConfigId namespacedConfigId = ConfigKeyGenerator.getConfigIdOfKey(message.getChannel());
                ConfigChangedEvent.Event event = ConfigChangedEvent.Event.of(message.getMessage());
                return new ConfigChangedEvent(namespacedConfigId, event);
            });
    }
    
    @Override
    public Flux<String> getConfigs(String namespace) {
        return delegate.getConfigs(namespace);
    }
    
    @Override
    public Mono<Config> getConfig(String namespace, String configId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(configId), "configId can not be empty!");
    
        return configMapCache.computeIfAbsent(NamespacedConfigId.of(namespace, configId), this::listenAndGetCache);
    }
    
    private Mono<Config> listenAndGetCache(NamespacedConfigId cfgId) {
        final String namespace = cfgId.getNamespace();
        final String configId = cfgId.getConfigId();
        listen(namespace, configId)
            .subscribe(new ConfigChangedEventSubscriber(this));
        return delegate.getConfig(namespace, configId).cache();
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
    public Mono<Boolean> rollback(String configId, int targetVersion) {
        return delegate.rollback(configId, targetVersion);
    }
    
    @Override
    public Mono<Boolean> rollback(String namespace, String configId, int targetVersion) {
        return delegate.rollback(namespace, configId, targetVersion);
    }
    
    @Override
    public Flux<ConfigVersion> getConfigVersions(String namespace, String configId) {
        return delegate.getConfigVersions(namespace, configId);
    }
    
    @Override
    public Mono<ConfigHistory> getConfigHistory(String namespace, String configId, int version) {
        return delegate.getConfigHistory(namespace, configId, version);
    }
    
    private void onConfigChanged(ConfigChangedEvent configChangedEvent) {
        NamespacedConfigId namespacedConfigId = configChangedEvent.getNamespacedConfigId();
        configMapCache.put(namespacedConfigId, delegate.getConfig(namespacedConfigId.getNamespace(), namespacedConfigId.getConfigId()).cache());
        if (null != hookOnResetCache) {
            hookOnResetCache.accept(configChangedEvent);
        }
    }
    
    public static class ConfigChangedEventSubscriber extends BaseSubscriber<ConfigChangedEvent> {
        private final ConsistencyRedisConfigService configService;
        
        public ConfigChangedEventSubscriber(ConsistencyRedisConfigService configService) {
            this.configService = configService;
        }
        
        @Override
        protected void hookOnNext(ConfigChangedEvent value) {
            if (log.isInfoEnabled()) {
                log.info("hookOnNext - NamespacedConfigId:[{}] - Event:[{}].", value.getNamespacedConfigId(), value.getEvent());
            }
            configService.onConfigChanged(value);
        }
        
        @Override
        protected void hookOnError(Throwable throwable) {
            if (log.isErrorEnabled()) {
                log.error("hookOnError - " + throwable.getMessage(), throwable);
            }
        }
    }
    
}
