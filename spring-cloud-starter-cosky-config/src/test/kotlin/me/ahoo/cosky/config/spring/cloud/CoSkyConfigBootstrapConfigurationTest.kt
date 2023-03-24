package me.ahoo.cosky.config.spring.cloud

import me.ahoo.cosky.config.ConfigEventListenerContainer
import me.ahoo.cosky.config.redis.RedisConfigService
import me.ahoo.cosky.config.redis.RedisConsistencyConfigService
import me.ahoo.cosky.config.spring.cloud.refresh.CoSkyConfigRefresher
import me.ahoo.cosky.spring.cloud.CoSkyAutoConfiguration
import org.assertj.core.api.AssertionsForInterfaceTypes
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner

internal class CoSkyConfigBootstrapConfigurationTest {
    private val contextRunner = ApplicationContextRunner()

    @Test
    fun contextLoads() {
        contextRunner
            .withPropertyValues("spring.cloud.cosky.namespace=contextLoads", "spring.application.name=app")
            .withUserConfiguration(
                RedisAutoConfiguration::class.java,
                RedisReactiveAutoConfiguration::class.java,
                CoSkyAutoConfiguration::class.java,
                CoSkyConfigBootstrapConfiguration::class.java,
            )
            .run {
                AssertionsForInterfaceTypes.assertThat(it)
                    .hasSingleBean(CoSkyConfigProperties::class.java)
                    .hasSingleBean(ConfigEventListenerContainer::class.java)
                    .hasSingleBean(RedisConfigService::class.java)
                    .hasSingleBean(RedisConsistencyConfigService::class.java)
                    .hasSingleBean(CoSkyConfigRefresher::class.java)
                    .hasSingleBean(CoSkyPropertySourceLocator::class.java)
            }
    }
}
