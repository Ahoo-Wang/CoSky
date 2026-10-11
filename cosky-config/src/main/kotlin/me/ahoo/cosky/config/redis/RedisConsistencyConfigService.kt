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

import me.ahoo.cosky.config.Config
import me.ahoo.cosky.config.ConfigEventListenerContainer
import me.ahoo.cosky.config.ConfigService
import me.ahoo.cosky.config.ConfigWatcher
import me.ahoo.cosky.config.NamespacedConfigId
import me.ahoo.cosky.core.cache.ReactiveWatchCache
import me.ahoo.cosky.core.cache.Snapshot
import me.ahoo.cosky.core.cache.WatchCache
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration

/**
 * Consistency Redis Config Service.
 *
 * Serves [getConfig] from a local cache kept consistent by config change events.
 *
 * @author ahoo wang
 */
class RedisConsistencyConfigService(
    private val delegate: ConfigService,
    configEventListenerContainer: ConfigEventListenerContainer,
    resyncInterval: Duration? = ReactiveWatchCache.DEFAULT_RESYNC_INTERVAL,
    retry: Retry = ReactiveWatchCache.DEFAULT_RETRY
) : ConfigService by delegate, ConfigWatcher, AutoCloseable {

    private val configCache: WatchCache<NamespacedConfigId, Config> = ReactiveWatchCache(
        listen = { configEventListenerContainer.listen(it) },
        load = { delegate.getConfig(it.namespace, it.configId) },
        resyncInterval = resyncInterval,
        retry = retry,
    )

    override fun getConfig(namespace: String, configId: String): Mono<Config> {
        return configCache.get(namespacedConfigId(namespace, configId))
    }

    override fun watchConfig(namespace: String, configId: String): Flux<Snapshot<Config>> {
        return configCache.watch(namespacedConfigId(namespace, configId))
    }

    private fun namespacedConfigId(namespace: String, configId: String): NamespacedConfigId {
        require(namespace.isNotBlank()) { "namespace can not be blank!" }
        require(configId.isNotBlank()) { "configId can not be blank!" }
        return NamespacedConfigId(namespace, configId)
    }

    override fun close() {
        configCache.close()
    }
}
