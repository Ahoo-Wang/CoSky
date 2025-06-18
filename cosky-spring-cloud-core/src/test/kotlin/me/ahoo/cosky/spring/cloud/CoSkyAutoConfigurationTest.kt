package me.ahoo.cosky.spring.cloud

import me.ahoo.cosky.core.NamespaceService
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner

internal class CoSkyAutoConfigurationTest {
    private val contextRunner = ApplicationContextRunner()

    @Test
    fun contextLoads() {
        contextRunner
            .withPropertyValues("spring.cloud.cosky.namespace=contextLoads")
            .withUserConfiguration(
                RedisAutoConfiguration::class.java,
                RedisReactiveAutoConfiguration::class.java,
                CoSkyAutoConfiguration::class.java,
            )
            .run {
                assertThat(it)
                    .hasSingleBean(CoSkyProperties::class.java)
                    .hasSingleBean(NamespaceService::class.java)
                    .getBean(CoSkyProperties::class.java)
                    .extracting {
                        assertThat(it.enabled).isEqualTo(true)
                        assertThat(it.namespace).isEqualTo("{contextLoads}")
                    }
            }
    }

    @Test
    fun contextLoadsWhenNotEnabled() {
        contextRunner
            .withPropertyValues(
                "${ConditionalOnCoSkyEnabled.ENABLED_KEY}=false",
                "spring.cloud.cosky.namespace=contextLoads",
            )
            .withUserConfiguration(
                RedisAutoConfiguration::class.java,
                RedisReactiveAutoConfiguration::class.java,
                CoSkyAutoConfiguration::class.java,
            )
            .run {
                assertThat(it)
                    .doesNotHaveBean(CoSkyProperties::class.java)
                    .doesNotHaveBean(NamespaceService::class.java)
            }
    }
}
