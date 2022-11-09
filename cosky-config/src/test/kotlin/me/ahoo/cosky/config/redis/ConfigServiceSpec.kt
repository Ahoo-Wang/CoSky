package me.ahoo.cosky.config.redis

import me.ahoo.cosid.test.MockIdGenerator
import me.ahoo.cosky.config.ConfigRollback
import me.ahoo.cosky.config.ConfigService
import me.ahoo.cosky.test.AbstractReactiveRedisTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test
import reactor.kotlin.test.test

abstract class ConfigServiceSpec : AbstractReactiveRedisTest() {
    lateinit var configService: ConfigService

    override fun afterInitializedRedisClient() {
        configService = createConfigService()
    }

    abstract fun createConfigService(): ConfigService

    @Test
    open fun setConfig() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        val testConfigId = MockIdGenerator.INSTANCE.generateAsString()
        configService.setConfig(namespace, testConfigId, "setConfigData")
            .test()
            .expectNext(true)
            .verifyComplete()
    }

    @Test
    open fun removeConfig() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        val testConfigId = MockIdGenerator.INSTANCE.generateAsString()
        configService.setConfig(namespace, testConfigId, "removeConfigData")
            .then(configService.removeConfig(namespace, testConfigId))
            .test()
            .expectNext(true)
            .verifyComplete()
    }

    @Test
    open fun getConfig() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        val testConfigId = MockIdGenerator.INSTANCE.generateAsString()
        val getConfigData = "getConfigData"
        configService.setConfig(namespace, testConfigId, getConfigData)
            .test()
            .expectNext(true)
            .verifyComplete()
        configService.getConfig(namespace, testConfigId)
            .test()
            .expectNextMatches {
                assertThat(testConfigId, equalTo(it.configId))
                assertThat(getConfigData, equalTo(it.data))
                assertThat(1, equalTo(it.version))
                true
            }
            .verifyComplete()
    }

    @Test
    open fun rollback() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        val testConfigId = MockIdGenerator.INSTANCE.generateAsString()
        val version1Data = "version-1"
        configService.setConfig(namespace, testConfigId, version1Data)
            .test()
            .expectNext(true)
            .verifyComplete()
        configService.getConfig(namespace, testConfigId)
            .test()
            .expectNextMatches {
                assertThat(it.data, equalTo(version1Data))
                true
            }
            .verifyComplete()
        val version2Data = "version-2"
        configService.setConfig(namespace, testConfigId, version2Data)
            .test()
            .expectNext(true)
            .verifyComplete()
        configService.getConfig(namespace, testConfigId)
            .test()
            .expectNextMatches {
                assertThat(it.data, equalTo(version2Data))
                true
            }
            .verifyComplete()
        configService.rollback(namespace, testConfigId, 1)
            .test()
            .expectNext(true)
            .verifyComplete()
        configService.getConfig(namespace, testConfigId)
            .test()
            .expectNextMatches {
                assertThat(it.data, equalTo(version1Data))
                true
            }
            .verifyComplete()
    }

    @Test
    open fun getConfigs() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        val testConfigId = MockIdGenerator.INSTANCE.generateAsString()
        configService.getConfigs(namespace)
            .test()
            .expectNextCount(0)
            .verifyComplete()
        configService.setConfig(namespace, testConfigId, "getConfigsData")
            .test()
            .expectNext(true)
            .verifyComplete()
        configService.getConfigs(namespace)
            .test()
            .expectNext(testConfigId)
            .verifyComplete()
    }

    @Test
    open fun getConfigVersions() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        val testConfigId = MockIdGenerator.INSTANCE.generateAsString()
        getConfigVersions(namespace, testConfigId)
    }

    private fun getConfigVersions(namespace: String, testConfigId: String) {
        configService.setConfig(namespace, testConfigId, "getConfigVersionData")
            .test()
            .expectNext(true)
            .verifyComplete()
        configService.getConfigVersions(namespace, testConfigId)
            .test()
            .expectNextCount(0)
            .verifyComplete()
        configService.setConfig(namespace, testConfigId, "getConfigVersionData-1")
            .test()
            .expectNext(true)
            .verifyComplete()
        configService.getConfigVersions(namespace, testConfigId)
            .test()
            .expectNextMatches {
                assertThat(it.configId, equalTo(testConfigId))
                assertThat(it.version, equalTo(1))
                true
            }
            .verifyComplete()
    }

    @Test
    open fun getConfigVersionsLast10() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        val testConfigId = MockIdGenerator.INSTANCE.generateAsString()
        for (i in 0 until ConfigRollback.HISTORY_SIZE * 2 + 1) {
            configService.setConfig(namespace, testConfigId, "getConfigVersionData-$i")
                .test()
                .expectNext(true)
                .verifyComplete()
        }
        configService.getConfigVersions(namespace, testConfigId).collectList()
            .test()
            .expectNextMatches {
                assertThat(it.size, equalTo(ConfigRollback.HISTORY_SIZE))
                val configVersion = it.first()
                assertThat(configVersion.configId, equalTo(testConfigId))
                assertThat(configVersion.version, equalTo(ConfigRollback.HISTORY_SIZE * 2))
                true
            }
            .verifyComplete()
    }

    @Test
    open fun getConfigHistory() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        val testConfigId = MockIdGenerator.INSTANCE.generateAsString()
        getConfigVersions(namespace, testConfigId)
        configService.getConfigHistory(namespace, testConfigId, 1)
            .test()
            .expectNextMatches {
                assertThat(it.configId, equalTo(testConfigId))
                assertThat(it.version, equalTo(1))
                true
            }
            .verifyComplete()
    }
}
