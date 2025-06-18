package me.ahoo.cosky.discovery.spring.cloud.registry

import me.ahoo.cosky.discovery.RegistryProperties
import me.ahoo.cosky.discovery.RenewInstanceService
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry
import me.ahoo.cosky.discovery.spring.cloud.support.StatusConstants
import me.ahoo.test.asserts.assert
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
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
                        it.serviceId.assert().isEqualTo("")
                        it.schema.assert().isEqualTo("http")
                        it.host.assert().isEqualTo("")
                        it.port.assert().isEqualTo(0)
                        it.weight.assert().isEqualTo(1)
                        it.isEphemeral.assert().isEqualTo(true)
                        it.ttl.assert().isEqualTo(Duration.ofSeconds(60))
                        it.timeout.assert().isEqualTo(Duration.ofSeconds(2))
                        it.metadata.assert().containsKey(StatusConstants.INSTANCE_STATUS_KEY)
                        it.initialStatus.assert().isEqualTo(StatusConstants.STATUS_UP)
                        it.renew.assert().isNotNull()
                        it.renew.initialDelay.assert().isEqualTo(Duration.ofSeconds(1))
                        it.renew.period.assert().isEqualTo(Duration.ofSeconds(10))
                    }
                assertThat(it)
                    .getBean(CoSkyRegistration::class.java)
                    .extracting {
                        it.serviceId.assert().isEqualTo("app")
                        it.scheme.assert().isEqualTo("http")
                        it.host.assert().isNotEmpty()
                        it.port.assert().isEqualTo(0)
                        it.weight.assert().isEqualTo(1)
                        it.isEphemeral.assert().isTrue()
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
                        it.serviceId.assert().isEqualTo("CustomizeServiceId")
                        it.schema.assert().isEqualTo("https")
                        it.host.assert().isEqualTo("host")
                        it.port.assert().isEqualTo(1024)
                        it.weight.assert().isEqualTo(100)
                        it.isEphemeral.assert().isFalse()
                        it.ttl.assert().isEqualTo(Duration.ofSeconds(120))
                        it.timeout.assert().isEqualTo(Duration.ofSeconds(2))
                        it.metadata.assert().containsKey("key").containsKey(StatusConstants.INSTANCE_STATUS_KEY)
                        it.initialStatus.assert().isEqualTo("OUT_OF_SERVICE")
                        it.renew.assert().isNotNull()
                        it.renew.initialDelay.assert().isEqualTo(Duration.ofSeconds(2))
                        it.renew.period.assert().isEqualTo(Duration.ofSeconds(10))
                    }
                assertThat(it)
                    .getBean(CoSkyRegistration::class.java)
                    .extracting {
                        it.serviceId.assert().isEqualTo("CustomizeServiceId")
                        it.scheme.assert().isEqualTo("https")
                        it.host.assert().isEqualTo("host")
                        it.port.assert().isEqualTo(1024)
                        it.weight.assert().isEqualTo(100)
                        it.isEphemeral.assert().isFalse()
                    }
            }
    }
}
