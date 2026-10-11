package me.ahoo.cosky.spring.cloud

import me.ahoo.cosky.core.NamespaceService
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.cloud.autoconfigure.ConfigurationPropertiesRebinderAutoConfiguration
import org.springframework.cloud.context.properties.ConfigurationPropertiesRebinder
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer

internal class CoSkyAutoConfigurationTest {
    private val contextRunner = ApplicationContextRunner()

    @Test
    fun contextLoads() {
        contextRunner
            .withPropertyValues("spring.cloud.cosky.namespace=contextLoads")
            .withUserConfiguration(
                DataRedisAutoConfiguration::class.java,
                DataRedisReactiveAutoConfiguration::class.java,
                CoSkyAutoConfiguration::class.java,
            )
            .run {
                assertThat(it)
                    .hasSingleBean(CoSkyProperties::class.java)
                    .hasSingleBean(NamespaceService::class.java)
                    .hasSingleBean(ReactiveRedisMessageListenerContainer::class.java)
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
                DataRedisAutoConfiguration::class.java,
                DataRedisReactiveAutoConfiguration::class.java,
                CoSkyAutoConfiguration::class.java,
            )
            .run {
                assertThat(it)
                    .doesNotHaveBean(CoSkyProperties::class.java)
                    .doesNotHaveBean(NamespaceService::class.java)
                    .doesNotHaveBean(ReactiveRedisMessageListenerContainer::class.java)
            }
    }

    /**
     * With Spring Cloud bootstrap, ConfigurationPropertiesRebinder rebinds every properties bean after startup:
     * the bound values must survive it.
     */
    @Test
    fun namespaceSurvivesRebind() {
        contextRunner
            .withPropertyValues("spring.cloud.cosky.namespace=cosky-{system}")
            .withUserConfiguration(
                DataRedisAutoConfiguration::class.java,
                DataRedisReactiveAutoConfiguration::class.java,
                ConfigurationPropertiesRebinderAutoConfiguration::class.java,
                CoSkyAutoConfiguration::class.java,
            )
            .run {
                it.getBean(ConfigurationPropertiesRebinder::class.java).rebind()
                assertThat(it.getBean(CoSkyProperties::class.java).namespace).isEqualTo("cosky-{system}")
            }
    }
}
