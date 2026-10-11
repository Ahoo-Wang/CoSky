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

import me.ahoo.cosid.test.MockIdGenerator
import me.ahoo.cosky.config.ConfigKeyGenerator
import me.ahoo.cosky.config.ConfigRollback
import me.ahoo.cosky.config.ConfigService
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.kotlin.test.test

/**
 * @author ahoo wang
 */
class RedisConfigServiceTest : ConfigServiceSpec() {

    override fun createConfigService(): ConfigService {
        return RedisConfigService(redisTemplate)
    }

    @Test
    fun rollbackRemovedConfig() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        val configId = MockIdGenerator.INSTANCE.generateAsString()
        val version1Data = "version-1"
        configService.setConfig(namespace, configId, version1Data)
            .then(configService.setConfig(namespace, configId, "version-2"))
            .then(configService.removeConfig(namespace, configId))
            .then(configService.rollback(namespace, configId, 1))
            .test()
            .expectNext(true)
            .verifyComplete()
        configService.getConfig(namespace, configId)
            .test()
            .expectNextMatches {
                it.data.assert().isEqualTo(version1Data)
                it.version.assert().isEqualTo(3)
                true
            }
            .verifyComplete()
    }

    @Test
    fun getConfigVersionsWithColonInConfigId() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        val configId = "${MockIdGenerator.INSTANCE.generateAsString()}:profile"
        configService.setConfig(namespace, configId, "version-1")
            .then(configService.setConfig(namespace, configId, "version-2"))
            .test()
            .expectNext(true)
            .verifyComplete()
        configService.getConfigVersions(namespace, configId)
            .test()
            .expectNextMatches {
                it.configId.assert().isEqualTo(configId)
                it.version.assert().isEqualTo(1)
                true
            }
            .verifyComplete()
    }

    /**
     * History beyond the last [ConfigRollback.HISTORY_SIZE] versions is deleted, not only hidden.
     */
    @Test
    fun historyIsTrimmedToHistorySize() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        val configId = MockIdGenerator.INSTANCE.generateAsString()
        Flux.range(1, 15)
            .concatMap { configService.setConfig(namespace, configId, "version-$it") }
            .then()
            .test()
            .verifyComplete()

        redisTemplate.opsForZSet().size(ConfigKeyGenerator.getConfigHistoryIdxKey(namespace, configId))
            .test()
            .expectNext(ConfigRollback.HISTORY_SIZE.toLong())
            .verifyComplete()
        configService.getConfigHistory(namespace, configId, 4).test().verifyComplete()
        configService.getConfigHistory(namespace, configId, 5)
            .test()
            .expectNextMatches { it.data == "version-5" }
            .verifyComplete()
    }
}
