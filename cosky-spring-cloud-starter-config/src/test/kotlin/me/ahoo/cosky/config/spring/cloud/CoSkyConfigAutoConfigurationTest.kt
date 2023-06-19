package me.ahoo.cosky.config.spring.cloud

import me.ahoo.cosky.config.ConfigEventListenerContainer
import me.ahoo.cosky.config.redis.RedisConfigService
import me.ahoo.cosky.config.redis.RedisConsistencyConfigService
import me.ahoo.cosky.config.spring.cloud.refresh.CoSkyConfigRefresher
import me.ahoo.cosky.spring.cloud.CoSkyAutoConfiguration
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Duration

internal class CoSkyConfigAutoConfigurationTest {
    private val contextRunner = ApplicationContextRunner()

    @Test
    fun contextLoads() {
        contextRunner
            .withPropertyValues("spring.cloud.cosky.namespace=contextLoads", "spring.application.name=app")
            .withUserConfiguration(
                RedisAutoConfiguration::class.java,
                RedisReactiveAutoConfiguration::class.java,
                CoSkyAutoConfiguration::class.java,
                CoSkyConfigAutoConfiguration::class.java,
            )
            .run {
                assertThat(it)
                    .hasSingleBean(CoSkyConfigProperties::class.java)
                    .hasSingleBean(ConfigEventListenerContainer::class.java)
                    .hasSingleBean(RedisConfigService::class.java)
                    .hasSingleBean(RedisConsistencyConfigService::class.java)
                    .hasSingleBean(CoSkyConfigRefresher::class.java)
                    .getBean(CoSkyConfigProperties::class.java)
                    .extracting {
                        assertThat(it.enabled).isEqualTo(true)
                        assertThat(it.configId).isEqualTo("app.yaml")
                        assertThat(it.timeout).isEqualTo(Duration.ofSeconds(2))
                    }
            }
    }

    @Test
    fun contextLoadsCustomizeProperties() {
        contextRunner
            .withPropertyValues(
                "spring.cloud.cosky.namespace=contextLoads",
                "spring.application.name=app",
                "spring.cloud.cosky.config.configId=test.yaml",
                "spring.cloud.cosky.config.timeout=3s",
            )
            .withUserConfiguration(
                RedisAutoConfiguration::class.java,
                RedisReactiveAutoConfiguration::class.java,
                CoSkyAutoConfiguration::class.java,
                CoSkyConfigAutoConfiguration::class.java,
            )
            .run {
                assertThat(it)
                    .hasSingleBean(CoSkyConfigProperties::class.java)
                    .hasSingleBean(ConfigEventListenerContainer::class.java)
                    .hasSingleBean(RedisConfigService::class.java)
                    .hasSingleBean(RedisConsistencyConfigService::class.java)
                    .hasSingleBean(CoSkyConfigRefresher::class.java)
                    .getBean(CoSkyConfigProperties::class.java)
                    .extracting {
                        assertThat(it.enabled).isEqualTo(true)
                        assertThat(it.configId).isEqualTo("test.yaml")
                        assertThat(it.timeout).isEqualTo(Duration.ofSeconds(3))
                    }
            }
    }
}
