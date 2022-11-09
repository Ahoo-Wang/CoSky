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
package me.ahoo.cosky.config.redis

import com.google.common.annotations.VisibleForTesting
import me.ahoo.cosky.config.Config
import me.ahoo.cosky.config.ConfigChangedEvent
import me.ahoo.cosky.config.ConfigChangedEvent.Companion.asConfigChangedEvent
import me.ahoo.cosky.config.ConfigKeyGenerator
import me.ahoo.cosky.config.ConfigService
import me.ahoo.cosky.config.ListenableConfigService
import me.ahoo.cosky.config.NamespacedConfigId
import org.slf4j.LoggerFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.core.publisher.BaseSubscriber
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private object NoOpHookOnResetCache : (ConfigChangedEvent) -> Unit {
    override fun invoke(p1: ConfigChangedEvent) = Unit
}

/**
 * Consistency Redis Config Service.
 * TODO CACHE
 * @author ahoo wang
 */
class ConsistencyRedisConfigService(
    private val delegate: ConfigService,
    private val listenerContainer: ReactiveRedisMessageListenerContainer,
    @VisibleForTesting
    private val hookOnResetCache: (ConfigChangedEvent) -> Unit = NoOpHookOnResetCache
) : ListenableConfigService, ConfigService by delegate {
    companion object {
        private val log = LoggerFactory.getLogger(ConsistencyRedisConfigService::class.java)
        val CONFIG_CACHE_TTL: Duration = Duration.ofMinutes(1)
    }

    private val configMapCache: ConcurrentHashMap<NamespacedConfigId, Mono<Config>> =
        ConcurrentHashMap<NamespacedConfigId, Mono<Config>>()

    override fun listen(topic: NamespacedConfigId): Flux<ConfigChangedEvent> {
        val topicStr: String = ConfigKeyGenerator.getConfigKey(topic.namespace, topic.configId)
        return listenerContainer
            .receive(ChannelTopic.of(topicStr))
            .map {
                val namespacedConfigId: NamespacedConfigId = ConfigKeyGenerator.getConfigIdOfKey(it.channel)
                val event = it.message.asConfigChangedEvent()
                ConfigChangedEvent(namespacedConfigId, event)
            }
    }

    override fun getConfig(namespace: String, configId: String): Mono<Config> {
        require(namespace.isNotBlank()) { "namespace can not be blank!" }
        require(configId.isNotBlank()) { "configId can not be blank!" }
        return configMapCache.computeIfAbsent(
            NamespacedConfigId(namespace, configId)
        ) { listenAndGetCache(it) }
    }

    private fun listenAndGetCache(cfgId: NamespacedConfigId): Mono<Config> {
        listen(cfgId)
            .subscribe(ConfigChangedEventSubscriber(this))
        return delegate.getConfig(cfgId.namespace, cfgId.configId).cache()
    }

    private fun onConfigChanged(configChangedEvent: ConfigChangedEvent) {
        val namespacedConfigId: NamespacedConfigId = configChangedEvent.namespacedConfigId
        configMapCache[namespacedConfigId] =
            @Suppress("ReactiveStreamsUnusedPublisher")
            delegate.getConfig(namespacedConfigId.namespace, namespacedConfigId.configId)
                .cache(CONFIG_CACHE_TTL)
        hookOnResetCache(configChangedEvent)
    }

    class ConfigChangedEventSubscriber(private val configService: ConsistencyRedisConfigService) :
        BaseSubscriber<ConfigChangedEvent>() {
        override fun hookOnNext(value: ConfigChangedEvent) {
            if (log.isInfoEnabled) {
                log.info("hookOnNext - NamespacedConfigId:[{}] - Event:[{}].", value.namespacedConfigId, value.event)
            }
            configService.onConfigChanged(value)
        }

        override fun hookOnError(throwable: Throwable) {
            if (log.isErrorEnabled) {
                log.error("hookOnError - " + throwable.message, throwable)
            }
        }
    }
}
