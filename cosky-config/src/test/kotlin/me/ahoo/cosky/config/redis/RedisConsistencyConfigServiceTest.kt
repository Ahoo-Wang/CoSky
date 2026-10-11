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
import me.ahoo.cosky.config.ConfigService
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.kotlin.test.test
import java.time.Duration

/**
 * @author ahoo wang
 */
internal class RedisConsistencyConfigServiceTest : ConfigServiceSpec() {
    lateinit var delegate: ConfigService
    lateinit var configEventListenerContainer: RedisConfigEventListenerContainer
    private val consistencyServices = mutableListOf<RedisConsistencyConfigService>()

    override fun createConfigService(): ConfigService {
        delegate = RedisConfigService(redisTemplate)
        configEventListenerContainer =
            RedisConfigEventListenerContainer(ReactiveRedisMessageListenerContainer(connectionFactory))
        return newConsistencyService()
    }

    private fun newConsistencyService(): RedisConsistencyConfigService {
        return RedisConsistencyConfigService(delegate, configEventListenerContainer).also {
            consistencyServices.add(it)
        }
    }

    override fun beforeDestroyRedisClient() {
        consistencyServices.forEach { it.close() }
        configEventListenerContainer.delegate.destroy()
    }

    private fun RedisConsistencyConfigService.awaitData(namespace: String, configId: String, data: String?) {
        watchConfig(namespace, configId)
            .filter { it.value?.data == data }
            .next()
            .test()
            .expectNextCount(1)
            .expectComplete()
            .verify(Duration.ofSeconds(5))
    }

    @Test
    fun config() {
        val namespace: String = MockIdGenerator.INSTANCE.generateAsString()
        val testConfigId: String = MockIdGenerator.INSTANCE.generateAsString()
        val configService = newConsistencyService()
        val getConfigData = "getConfigData"
        configService.setConfig(namespace, testConfigId, getConfigData)
            .test()
            .expectNext(true)
            .verifyComplete()
        configService.getConfig(namespace, testConfigId)
            .test()
            .expectNextMatches {
                it.configId.assert().isEqualTo(testConfigId)
                it.data.assert().isEqualTo(getConfigData)
                it.version.assert().isEqualTo(1)
                true
            }
            .verifyComplete()
        configService.getConfig(namespace, testConfigId)
            .zipWith(configService.getConfig(namespace, testConfigId))
            .test()
            .expectNextMatches { it.t1 === it.t2 }
            .verifyComplete()
    }

    @Test
    fun getConfigChanged() {
        val namespace: String = MockIdGenerator.INSTANCE.generateAsString()
        val testConfigId: String = MockIdGenerator.INSTANCE.generateAsString()
        val configService = newConsistencyService()
        configService.getConfig(namespace, testConfigId)
            .test()
            .verifyComplete()
        val configData: String = MockIdGenerator.INSTANCE.generateAsString()
        configService.setConfig(namespace, testConfigId, configData)
            .test()
            .expectNext(true)
            .verifyComplete()
        configService.awaitData(namespace, testConfigId, configData)
        configService.getConfig(namespace, testConfigId)
            .test()
            .expectNextMatches { configData == it.data }
            .verifyComplete()
        configService.getConfig(namespace, testConfigId)
            .zipWith(configService.getConfig(namespace, testConfigId))
            .test()
            .expectNextMatches { it.t1 === it.t2 }
            .verifyComplete()
    }

    @Test
    fun getConfigChangedRemove() {
        val namespace: String = MockIdGenerator.INSTANCE.generateAsString()
        val testConfigId: String = MockIdGenerator.INSTANCE.generateAsString()
        val configService = newConsistencyService()
        configService.getConfig(namespace, testConfigId)
            .test()
            .verifyComplete()
        val configData = "configData"
        configService.setConfig(namespace, testConfigId, configData)
            .test()
            .expectNext(true)
            .verifyComplete()
        configService.awaitData(namespace, testConfigId, configData)
        configService.removeConfig(namespace, testConfigId)
            .test()
            .expectNext(true)
            .verifyComplete()
        configService.awaitData(namespace, testConfigId, null)
        configService.getConfig(namespace, testConfigId)
            .test()
            .verifyComplete()
    }

    @Test
    fun getConfigChangedRollback() {
        val namespace: String = MockIdGenerator.INSTANCE.generateAsString()
        val testConfigId: String = MockIdGenerator.INSTANCE.generateAsString()
        val configService = newConsistencyService()
        configService.getConfig(namespace, testConfigId)
            .test()
            .verifyComplete()
        val version1Data = "version-1"
        configService.setConfig(namespace, testConfigId, version1Data)
            .test()
            .expectNext(true)
            .verifyComplete()
        configService.awaitData(namespace, testConfigId, version1Data)
        val version2Data = "version-2"
        configService.setConfig(namespace, testConfigId, version2Data)
            .test()
            .expectNext(true)
            .verifyComplete()
        configService.awaitData(namespace, testConfigId, version2Data)
        configService.rollback(namespace, testConfigId, 1)
            .test()
            .expectNext(true)
            .verifyComplete()
        configService.awaitData(namespace, testConfigId, version1Data)
        configService.getConfig(namespace, testConfigId)
            .test()
            .expectNextMatches { version1Data == it.data && it.version == 3 }
            .verifyComplete()
    }

    /**
     * A change written right after the first read must reach the cache: the read only starts once the
     * change subscription is active.
     */
    @Test
    fun changeRightAfterFirstReadIsNotLost() {
        repeat(20) {
            val namespace: String = MockIdGenerator.INSTANCE.generateAsString()
            val testConfigId: String = MockIdGenerator.INSTANCE.generateAsString()
            val configService = newConsistencyService()
            configService.getConfig(namespace, testConfigId).subscribe()
            configService.setConfig(namespace, testConfigId, "changed")
                .test()
                .expectNext(true)
                .verifyComplete()
            configService.awaitData(namespace, testConfigId, "changed")
        }
    }

    override fun rollback() = Unit
}
