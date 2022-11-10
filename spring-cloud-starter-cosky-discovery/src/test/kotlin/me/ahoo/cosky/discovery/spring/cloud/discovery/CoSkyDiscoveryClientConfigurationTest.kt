package me.ahoo.cosky.discovery.spring.cloud.discovery

import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.cloud.client.discovery.composite.reactive.ReactiveCompositeDiscoveryClientAutoConfiguration

internal class CoSkyDiscoveryClientConfigurationTest {
    private val contextRunner = ApplicationContextRunner()

    @Test
    fun contextLoads() {
        contextRunner
            .withPropertyValues("spring.cloud.cosky.namespace=contextLoads", "spring.application.name=app")
            .withUserConfiguration(
                RedisAutoConfiguration::class.java,
                RedisReactiveAutoConfiguration::class.java,
                CoSkyDiscoveryAutoConfiguration::class.java,
                ReactiveCompositeDiscoveryClientAutoConfiguration::class.java,
                CoSkyDiscoveryClientConfiguration::class.java
            )
            .run {
                assertThat(it)
                    .hasSingleBean(CoSkyDiscoveryClient::class.java)
            }
    }
}
