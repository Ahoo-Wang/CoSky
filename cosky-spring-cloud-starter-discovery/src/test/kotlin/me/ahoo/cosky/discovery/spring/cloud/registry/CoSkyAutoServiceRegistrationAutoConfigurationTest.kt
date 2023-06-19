package me.ahoo.cosky.discovery.spring.cloud.registry

import me.ahoo.cosky.discovery.RegistryProperties
import me.ahoo.cosky.discovery.RenewInstanceService
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry
import me.ahoo.cosky.discovery.spring.cloud.support.StatusConstants
import org.assertj.core.api.AssertionsForInterfaceTypes.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationConfiguration
import org.springframework.cloud.commons.util.UtilAutoConfiguration
import java.time.Duration

internal class CoSkyAutoServiceRegistrationAutoConfigurationTest {
    private val contextRunner = ApplicationContextRunner()

    @Test
    fun contextLoads() {
        contextRunner
            .withPropertyValues(
                "spring.cloud.cosky.namespace=contextLoads",
                "spring.application.name=app",
            )
            .withUserConfiguration(
                RedisAutoConfiguration::class.java,
                RedisReactiveAutoConfiguration::class.java,
                UtilAutoConfiguration::class.java,
                AutoServiceRegistrationConfiguration::class.java,
                CoSkyAutoServiceRegistrationAutoConfiguration::class.java,
            )
            .run {
                assertThat(it)
                    .hasSingleBean(CoSkyRegistryProperties::class.java)
                    .hasSingleBean(RegistryProperties::class.java)
                    .hasSingleBean(RedisServiceRegistry::class.java)
                    .hasSingleBean(RenewInstanceService::class.java)
                    .hasSingleBean(CoSkyRegistration::class.java)
                    .hasSingleBean(CoSkyServiceRegistry::class.java)
                    .hasSingleBean(CoSkyAutoServiceRegistration::class.java)
                    .hasSingleBean(CoSkyAutoServiceRegistrationOfNoneWeb::class.java)
                    .getBean(CoSkyRegistryProperties::class.java)
                    .extracting {
                        assertThat(it.serviceId).isEqualTo("")
                        assertThat(it.schema).isEqualTo("http")
                        assertThat(it.host).isEqualTo("")
                        assertThat(it.port).isEqualTo(0)
                        assertThat(it.weight).isEqualTo(1)
                        assertThat(it.isEphemeral).isEqualTo(true)
                        assertThat(it.ttl).isEqualTo(Duration.ofSeconds(60))
                        assertThat(it.timeout).isEqualTo(Duration.ofSeconds(2))
                        assertThat(it.metadata).containsKey(StatusConstants.INSTANCE_STATUS_KEY)
                        assertThat(it.initialStatus).isEqualTo(StatusConstants.STATUS_UP)
                        assertThat(it.renew).isNotNull()
                        assertThat(it.renew.initialDelay).isEqualTo(Duration.ofSeconds(1))
                        assertThat(it.renew.period).isEqualTo(Duration.ofSeconds(10))
                    }
                assertThat(it)
                    .getBean(CoSkyRegistration::class.java)
                    .extracting {
                        assertThat(it.serviceId).isEqualTo("app")
                        assertThat(it.scheme).isEqualTo("http")
                        assertThat(it.host).isNotEmpty()
                        assertThat(it.port).isEqualTo(0)
                        assertThat(it.weight).isEqualTo(1)
                        assertThat(it.isEphemeral).isEqualTo(true)
                    }
            }
    }

    @Test
    fun contextLoadsCustomizeProperties() {
        contextRunner
            .withPropertyValues(
                "spring.cloud.cosky.namespace=contextLoads",
                "${CoSkyRegistryProperties.PREFIX}.serviceId=CustomizeServiceId",
                "${CoSkyRegistryProperties.PREFIX}.schema=https",
                "${CoSkyRegistryProperties.PREFIX}.host=host",
                "${CoSkyRegistryProperties.PREFIX}.port=1024",
                "${CoSkyRegistryProperties.PREFIX}.weight=100",
                "${CoSkyRegistryProperties.PREFIX}.isEphemeral=false",
                "${CoSkyRegistryProperties.PREFIX}.ttl=120s",
                "${CoSkyRegistryProperties.PREFIX}.metadata.key=value",
                "${CoSkyRegistryProperties.PREFIX}.initial-status=OUT_OF_SERVICE",
                "${CoSkyRegistryProperties.PREFIX}.renew.initialDelay=2s",
            )
            .withUserConfiguration(
                RedisAutoConfiguration::class.java,
                RedisReactiveAutoConfiguration::class.java,
                UtilAutoConfiguration::class.java,
                AutoServiceRegistrationConfiguration::class.java,
                CoSkyAutoServiceRegistrationAutoConfiguration::class.java,
            )
            .run {
                assertThat(it)
                    .hasSingleBean(CoSkyRegistryProperties::class.java)
                    .hasSingleBean(RegistryProperties::class.java)
                    .hasSingleBean(RedisServiceRegistry::class.java)
                    .hasSingleBean(RenewInstanceService::class.java)
                    .hasSingleBean(CoSkyRegistration::class.java)
                    .hasSingleBean(CoSkyServiceRegistry::class.java)
                    .hasSingleBean(CoSkyAutoServiceRegistration::class.java)
                    .hasSingleBean(CoSkyAutoServiceRegistrationOfNoneWeb::class.java)
                    .getBean(CoSkyRegistryProperties::class.java)
                    .extracting {
                        assertThat(it.serviceId).isEqualTo("CustomizeServiceId")
                        assertThat(it.schema).isEqualTo("https")
                        assertThat(it.host).isEqualTo("host")
                        assertThat(it.port).isEqualTo(1024)
                        assertThat(it.weight).isEqualTo(100)
                        assertThat(it.isEphemeral).isEqualTo(false)
                        assertThat(it.ttl).isEqualTo(Duration.ofSeconds(120))
                        assertThat(it.timeout).isEqualTo(Duration.ofSeconds(2))
                        assertThat(it.metadata).containsKey("key").containsKey(StatusConstants.INSTANCE_STATUS_KEY)
                        assertThat(it.initialStatus).isEqualTo("OUT_OF_SERVICE")
                        assertThat(it.renew).isNotNull()
                        assertThat(it.renew.initialDelay).isEqualTo(Duration.ofSeconds(2))
                        assertThat(it.renew.period).isEqualTo(Duration.ofSeconds(10))
                    }
                assertThat(it)
                    .getBean(CoSkyRegistration::class.java)
                    .extracting {
                        assertThat(it.serviceId).isEqualTo("CustomizeServiceId")
                        assertThat(it.scheme).isEqualTo("https")
                        assertThat(it.host).isEqualTo("host")
                        assertThat(it.port).isEqualTo(1024)
                        assertThat(it.weight).isEqualTo(100)
                        assertThat(it.isEphemeral).isEqualTo(false)
                    }
            }
    }
}
