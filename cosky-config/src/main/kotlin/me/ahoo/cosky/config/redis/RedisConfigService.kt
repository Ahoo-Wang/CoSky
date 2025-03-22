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

import com.google.common.hash.Hashing
import io.github.oshai.kotlinlogging.KotlinLogging
import me.ahoo.cosky.config.Config
import me.ahoo.cosky.config.ConfigCodec.decodeAsConfig
import me.ahoo.cosky.config.ConfigCodec.decodeAsHistory
import me.ahoo.cosky.config.ConfigHistory
import me.ahoo.cosky.config.ConfigKeyGenerator.getConfigHistoryIdxKey
import me.ahoo.cosky.config.ConfigKeyGenerator.getConfigHistoryKey
import me.ahoo.cosky.config.ConfigKeyGenerator.getConfigIdOfKey
import me.ahoo.cosky.config.ConfigKeyGenerator.getConfigIdxKey
import me.ahoo.cosky.config.ConfigKeyGenerator.getConfigKey
import me.ahoo.cosky.config.ConfigKeyGenerator.getConfigVersionOfHistoryKey
import me.ahoo.cosky.config.ConfigRollback
import me.ahoo.cosky.config.ConfigService
import me.ahoo.cosky.config.ConfigVersion
import org.springframework.data.domain.Range
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

/**
 * Redis Config Service.
 *
 * @author ahoo wang
 */
class RedisConfigService(private val redisTemplate: ReactiveStringRedisTemplate) : ConfigService {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val HISTORY_STOP = (ConfigRollback.HISTORY_SIZE - 1).toLong()
    }

    override fun getConfigs(namespace: String): Flux<String> {
        require(namespace.isNotBlank()) { "namespace can not be blank!" }
        log.debug {
            "GetConfigs  @ namespace:[$namespace]."
        }
        val configIdxKey = getConfigIdxKey(namespace)
        return redisTemplate
            .opsForSet()
            .members(configIdxKey)
            .map {
                getConfigIdOfKey(it).configId
            }
    }

    override fun getConfig(namespace: String, configId: String): Mono<Config> {
        ensureNamespacedConfigId(namespace, configId)
        log.debug {
            "GetConfig - configId:[$configId]  @ namespace:[$namespace]."
        }
        val configKey = getConfigKey(namespace, configId)
        return getAndDecodeConfig(configKey) { it.decodeAsConfig() }
    }

    private fun ensureNamespacedConfigId(namespace: String, configId: String) {
        require(namespace.isNotBlank()) { "namespace can not be blank!" }
        require(configId.isNotBlank()) { "configId can not be blank!" }
    }

    override fun setConfig(namespace: String, configId: String, data: String): Mono<Boolean> {
        ensureNamespacedConfigId(namespace, configId)
        val hash = Hashing.sha256().hashString(data, StandardCharsets.UTF_8).toString()
        log.info {
            "SetConfig - configId:[$configId] - hash:[$hash]  @ namespace:[$namespace]."
        }
        return redisTemplate.execute(
            ConfigRedisScripts.SCRIPT_CONFIG_SET,
            listOf(namespace),
            listOf(configId, data, hash),
        ).next()
    }

    override fun removeConfig(namespace: String, configId: String): Mono<Boolean> {
        ensureNamespacedConfigId(namespace, configId)
        log.info {
            "RemoveConfig - configId:[$configId] @ namespace:[$namespace]."
        }
        return redisTemplate.execute(
            ConfigRedisScripts.SCRIPT_CONFIG_REMOVE,
            listOf(namespace),
            listOf(configId),
        ).next()
    }

    override fun containsConfig(namespace: String, configId: String): Mono<Boolean> {
        ensureNamespacedConfigId(namespace, configId)
        val configKey = getConfigKey(namespace, configId)
        return redisTemplate.hasKey(configKey)
    }

    override fun rollback(namespace: String, configId: String, targetVersion: Int): Mono<Boolean> {
        ensureNamespacedConfigId(namespace, configId)
        log.info {
            "Rollback - configId:[$configId] - targetVersion:[$targetVersion]  @ namespace:[$namespace]."
        }
        return redisTemplate.execute(
            ConfigRedisScripts.SCRIPT_CONFIG_ROLLBACK,
            listOf(namespace),
            listOf(configId, targetVersion.toString()),
        ).next()
    }

    override fun getConfigVersions(namespace: String, configId: String): Flux<ConfigVersion> {
        ensureNamespacedConfigId(namespace, configId)
        val configHistoryIdxKey = getConfigHistoryIdxKey(namespace, configId)
        return redisTemplate
            .opsForZSet()
            .reverseRange(
                configHistoryIdxKey,
                Range.closed(0L, HISTORY_STOP),
            )
            .map { getConfigVersionOfHistoryKey(namespace, it) }
    }

    override fun getConfigHistory(namespace: String, configId: String, version: Int): Mono<ConfigHistory> {
        ensureNamespacedConfigId(namespace, configId)
        val configHistoryKey = getConfigHistoryKey(namespace, configId, version)
        return getAndDecodeConfig(configHistoryKey) { it.decodeAsHistory() }
    }

    private fun <T : Config> getAndDecodeConfig(
        key: String,
        decode: (Map<String, String>) -> T
    ): Mono<T> {
        return redisTemplate
            .opsForHash<String, String>()
            .entries(key)
            .collectMap({ it.key }) { it.value }
            .mapNotNull {
                if (it.isEmpty()) {
                    return@mapNotNull null
                }
                decode(it)
            }
    }
}
