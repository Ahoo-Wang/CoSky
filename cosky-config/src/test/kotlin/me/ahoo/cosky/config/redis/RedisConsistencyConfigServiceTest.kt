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
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.kotlin.test.test
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * @author ahoo wang
 */
internal class RedisConsistencyConfigServiceTest : ConfigServiceSpec() {
    lateinit var delegate: ConfigService
    lateinit var configEventListenerContainer: RedisConfigEventListenerContainer
    override fun createConfigService(): ConfigService {
        delegate = RedisConfigService(redisTemplate)
        configEventListenerContainer =
            RedisConfigEventListenerContainer(ReactiveRedisMessageListenerContainer(connectionFactory))
        return RedisConsistencyConfigService(delegate, configEventListenerContainer)
    }

    @Test
    fun config() {
        val namespace: String = MockIdGenerator.INSTANCE.generateAsString()
        val testConfigId: String = MockIdGenerator.INSTANCE.generateAsString()
        val configService: ConfigService = RedisConsistencyConfigService(delegate, configEventListenerContainer)
        val getConfigData = "getConfigData"
        configService.setConfig(namespace, testConfigId, getConfigData)
            .test()
            .expectNext(true)
            .verifyComplete()
        configService.getConfig(namespace, testConfigId)
            .test()
            .expectNextMatches {
                assertThat(it.configId, equalTo(testConfigId))
                assertThat(it.data, equalTo(getConfigData))
                assertThat(it.version, equalTo(1))
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
        val semaphore = Semaphore(0)
        val configService: ConfigService = RedisConsistencyConfigService(
            delegate,
            configEventListenerContainer
        ) { (namespacedConfigId) ->
            if (namespacedConfigId.namespace == namespace) {
                semaphore.release()
            }
        }
        configService.getConfig(namespace, testConfigId)
            .test()
            .expectNextCount(0)
            .verifyComplete()
        val configData: String = MockIdGenerator.INSTANCE.generateAsString()
        configService.setConfig(namespace, testConfigId, configData)
            .test()
            .expectNext(true)
            .verifyComplete()
        Assertions.assertTrue(semaphore.tryAcquire(5, TimeUnit.SECONDS))
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
        val semaphore = Semaphore(0)
        val configService: ConfigService = RedisConsistencyConfigService(
            delegate,
            configEventListenerContainer
        ) { semaphore.release() }
        configService.getConfig(namespace, testConfigId)
            .test()
            .expectNextCount(0)
            .verifyComplete()
        val configData = "configData"
        configService.setConfig(namespace, testConfigId, configData)
            .test()
            .expectNext(true)
            .verifyComplete()
        Assertions.assertTrue(semaphore.tryAcquire(5, TimeUnit.SECONDS))
        configService.getConfig(namespace, testConfigId)
            .test()
            .expectNextMatches { configData == it.data }
            .verifyComplete()
        configService.removeConfig(namespace, testConfigId)
            .test()
            .expectNext(true)
            .verifyComplete()
        Assertions.assertTrue(semaphore.tryAcquire(5, TimeUnit.SECONDS))
        configService.getConfig(namespace, testConfigId)
            .test()
            .expectNextCount(0)
            .verifyComplete()
    }

    @Test
    fun getConfigChangedRollback() {
        val namespace: String = MockIdGenerator.INSTANCE.generateAsString()
        val testConfigId: String = MockIdGenerator.INSTANCE.generateAsString()
        val semaphore = Semaphore(0)
        val configService: ConfigService = RedisConsistencyConfigService(
            delegate,
            configEventListenerContainer
        ) { semaphore.release() }
        configService.getConfig(namespace, testConfigId)
            .test()
            .expectNextCount(0)
            .verifyComplete()
        val version1Data = "version-1"
        configService.setConfig(namespace, testConfigId, version1Data)
            .test()
            .expectNext(true)
            .verifyComplete()
        Assertions.assertTrue(semaphore.tryAcquire(5, TimeUnit.SECONDS))
        configService.getConfig(namespace, testConfigId)
            .test()
            .expectNextMatches { version1Data == it.data }
            .verifyComplete()
        val version2Data = "version-2"
        configService.setConfig(namespace, testConfigId, version2Data)
            .test()
            .expectNext(true)
            .verifyComplete()
        Assertions.assertTrue(semaphore.tryAcquire(5, TimeUnit.SECONDS))
        configService.getConfig(namespace, testConfigId)
            .test()
            .expectNextMatches { version2Data == it.data }
            .verifyComplete()
        configService.rollback(namespace, testConfigId, 1)
            .test()
            .expectNext(true)
            .verifyComplete()
        Assertions.assertTrue(semaphore.tryAcquire(5, TimeUnit.SECONDS))
        configService.getConfig(namespace, testConfigId)
            .test()
            .expectNextMatches { version1Data == it.data }
            .verifyComplete()
    }

    override fun rollback() {
    }
}
