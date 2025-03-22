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

import io.github.oshai.kotlinlogging.KotlinLogging
import me.ahoo.cosky.config.Config
import me.ahoo.cosky.config.ConfigChangedEvent
import me.ahoo.cosky.config.ConfigEventListenerContainer
import me.ahoo.cosky.config.ConfigService
import me.ahoo.cosky.config.NamespacedConfigId
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private object NoOpHookOnResetCache : (ConfigChangedEvent) -> Unit {
    override fun invoke(p1: ConfigChangedEvent) = Unit
}

/**
 * Consistency Redis Config Service.
 * @author ahoo wang
 */
class RedisConsistencyConfigService(
    private val delegate: ConfigService,
    private val configEventListenerContainer: ConfigEventListenerContainer,
    private val hookOnResetCache: (ConfigChangedEvent) -> Unit = NoOpHookOnResetCache
) : ConfigService by delegate {
    companion object {
        private val log = KotlinLogging.logger {}
        val CONFIG_CACHE_TTL: Duration = Duration.ofMinutes(1)
    }

    private val configMapCache: ConcurrentHashMap<NamespacedConfigId, Mono<Config>> =
        ConcurrentHashMap<NamespacedConfigId, Mono<Config>>()

    override fun getConfig(namespace: String, configId: String): Mono<Config> {
        require(namespace.isNotBlank()) { "namespace can not be blank!" }
        require(configId.isNotBlank()) { "configId can not be blank!" }
        val namespacedConfigId = NamespacedConfigId(namespace, configId)
        return configMapCache.computeIfAbsent(
            namespacedConfigId,
        ) {
            @Suppress("CallingSubscribeInNonBlockingScope")
            configEventListenerContainer.receive(it)
                .doOnNext { changedEvent ->
                    onConfigChanged(changedEvent)
                }.doFinally { signalType ->
                    log.info {
                        "Listen topic[$namespacedConfigId] finally - [$signalType]."
                    }
                    configMapCache.remove(namespacedConfigId)
                }
                .subscribe()
            delegate.getConfig(it.namespace, it.configId).cache()
        }
    }

    private fun onConfigChanged(configChangedEvent: ConfigChangedEvent) {
        log.info {
            "onConfigChanged:$configChangedEvent"
        }
        val namespacedConfigId: NamespacedConfigId = configChangedEvent.namespacedConfigId
        configMapCache[namespacedConfigId] =
            @Suppress("ReactiveStreamsUnusedPublisher")
            delegate.getConfig(namespacedConfigId.namespace, namespacedConfigId.configId)
                .cache(CONFIG_CACHE_TTL)
        hookOnResetCache(configChangedEvent)
    }
}
