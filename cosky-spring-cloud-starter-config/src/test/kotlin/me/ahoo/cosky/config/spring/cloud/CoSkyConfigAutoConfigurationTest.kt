package me.ahoo.cosky.config.spring.cloud

import me.ahoo.cosky.config.ConfigEventListenerContainer
import me.ahoo.cosky.config.redis.RedisConfigService
import me.ahoo.cosky.config.redis.RedisConsistencyConfigService
import me.ahoo.cosky.config.spring.cloud.refresh.CoSkyConfigRefresher
import me.ahoo.cosky.spring.cloud.CoSkyAutoConfiguration
import me.ahoo.test.asserts.assert
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Duration
import kotlin.jvm.java

internal class CoSkyConfigAutoConfigurationTest {
    private val contextRunner = ApplicationContextRunner()

    @Test
    fun contextLoads() {
        contextRunner
            .withPropertyValues("spring.cloud.cosky.namespace=contextLoads", "spring.application.name=app")
            .withUserConfiguration(
                DataRedisAutoConfiguration::class.java,
                DataRedisReactiveAutoConfiguration::class.java,
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
                        it.enabled.assert().isTrue()
                        it.configId.assert().isEqualTo("app.yaml")
                        it.timeout.assert().isEqualTo(Duration.ofSeconds(2))
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
                DataRedisAutoConfiguration::class.java,
                DataRedisReactiveAutoConfiguration::class.java,
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
                        it.enabled.assert().isTrue()
                        it.configId.assert().isEqualTo("test.yaml")
                        it.timeout.assert().isEqualTo(Duration.ofSeconds(3))
                    }
            }
    }
}
