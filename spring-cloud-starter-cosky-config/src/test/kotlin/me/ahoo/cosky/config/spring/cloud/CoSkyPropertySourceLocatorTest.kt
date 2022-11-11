package me.ahoo.cosky.config.spring.cloud

import io.mockk.mockk
import me.ahoo.cosid.test.MockIdGenerator
import me.ahoo.cosky.config.ConfigService
import me.ahoo.cosky.config.redis.RedisConfigService
import me.ahoo.cosky.core.NamespacedContext
import me.ahoo.cosky.test.AbstractReactiveRedisTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*

import org.junit.jupiter.api.Test
import org.springframework.boot.origin.OriginTrackedValue
import reactor.kotlin.test.test

internal class CoSkyPropertySourceLocatorTest : AbstractReactiveRedisTest() {
    lateinit var configService: ConfigService

    companion object {
        const val configData = """
        spring:
          cloud:
            cosky:
              discovery:
                registry:
                  weight: 8
        cosid:
          namespace: service
          machine:
            enabled: true
            distributor:
              type: redis
          snowflake:
            enabled: true
            share:
              converter:
                type: radix
        simba:
          redis:
            enabled: true
    """
    }

    override fun afterInitializedRedisClient() {
        configService = RedisConfigService(redisTemplate)
    }

    @Test
    fun locateNone() {
        val properties = CoSkyConfigProperties(configId = "${MockIdGenerator.INSTANCE.generateAsString()}.yaml")
        val coSkyPropertySourceLocator = CoSkyPropertySourceLocator(properties, configService)
        val propertySources = coSkyPropertySourceLocator.locate(mockk())
        assertThat(propertySources, notNullValue())
    }

    @Test
    fun locate() {
        configService.setConfig(NamespacedContext.namespace, "service.yaml", configData)
            .test()
            .expectNextCount(1)
            .verifyComplete()
        val properties = CoSkyConfigProperties(configId = "service.yaml")
        val coSkyPropertySourceLocator = CoSkyPropertySourceLocator(properties, configService)
        val propertySources = coSkyPropertySourceLocator.locate(mockk())
        assertThat(propertySources, notNullValue())
        assertThat(propertySources.source, isA(Map::class.java))
        @Suppress("UNCHECKED_CAST")
        val source = propertySources.source as Map<String, OriginTrackedValue>
        assertThat(source["spring.cloud.cosky.discovery.registry.weight"]?.value, `is`(8))
        assertThat(source["cosid.namespace"]?.value, equalTo("service"))
    }
}
