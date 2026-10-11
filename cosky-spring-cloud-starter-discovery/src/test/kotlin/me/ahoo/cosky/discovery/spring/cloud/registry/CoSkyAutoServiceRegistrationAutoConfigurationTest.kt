/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.ahoo.cosky.discovery.spring.cloud.registry

import me.ahoo.cosky.discovery.RegistryProperties
import me.ahoo.cosky.discovery.RenewInstanceService
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry
import me.ahoo.cosky.discovery.spring.cloud.support.StatusConstants
import me.ahoo.cosky.spring.cloud.CoSkyAutoConfiguration
import me.ahoo.test.asserts.assert
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.cloud.autoconfigure.ConfigurationPropertiesRebinderAutoConfiguration
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationConfiguration
import org.springframework.cloud.commons.util.UtilAutoConfiguration
import org.springframework.cloud.context.properties.ConfigurationPropertiesRebinder
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
                DataRedisAutoConfiguration::class.java,
                DataRedisReactiveAutoConfiguration::class.java,
                UtilAutoConfiguration::class.java,
                AutoServiceRegistrationConfiguration::class.java,
                CoSkyAutoConfiguration::class.java,
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
                        it.metadata.assert().isEmpty()
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
                "${CoSkyRegistryProperties.PREFIX}.renew-event-carries-ttl-at=true",
            )
            .withUserConfiguration(
                DataRedisAutoConfiguration::class.java,
                DataRedisReactiveAutoConfiguration::class.java,
                UtilAutoConfiguration::class.java,
                AutoServiceRegistrationConfiguration::class.java,
                CoSkyAutoConfiguration::class.java,
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
                    .getBean(RegistryProperties::class.java)
                    .extracting {
                        it.instanceTtl.assert().isEqualTo(Duration.ofSeconds(120))
                        it.renewEventCarriesTtlAt.assert().isTrue()
                    }
                assertCustomized(it.getBean(CoSkyRegistryProperties::class.java))
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

    private fun assertCustomized(properties: CoSkyRegistryProperties) {
        properties.serviceId.assert().isEqualTo("CustomizeServiceId")
        properties.schema.assert().isEqualTo("https")
        properties.host.assert().isEqualTo("host")
        properties.port.assert().isEqualTo(1024)
        properties.weight.assert().isEqualTo(100)
        properties.isEphemeral.assert().isFalse()
        properties.ttl.assert().isEqualTo(Duration.ofSeconds(120))
        properties.timeout.assert().isEqualTo(Duration.ofSeconds(2))
        properties.metadata.assert().isEqualTo(mapOf("key" to "value"))
        properties.initialStatus.assert().isEqualTo("OUT_OF_SERVICE")
        properties.renew.assert().isNotNull()
        properties.renew.initialDelay.assert().isEqualTo(Duration.ofSeconds(2))
        properties.renew.period.assert().isEqualTo(Duration.ofSeconds(10))
    }

    @Test
    fun contextLoadsWhenCoSkyDisabled() {
        contextRunner
            .withPropertyValues("spring.cloud.cosky.enabled=false", "spring.application.name=app")
            .withUserConfiguration(
                DataRedisAutoConfiguration::class.java,
                DataRedisReactiveAutoConfiguration::class.java,
                UtilAutoConfiguration::class.java,
                AutoServiceRegistrationConfiguration::class.java,
                CoSkyAutoConfiguration::class.java,
                CoSkyAutoServiceRegistrationAutoConfiguration::class.java,
            )
            .run {
                assertThat(it)
                    .doesNotHaveBean(RedisServiceRegistry::class.java)
                    .doesNotHaveBean(CoSkyRegistration::class.java)
            }
    }

    /**
     * With Spring Cloud bootstrap, ConfigurationPropertiesRebinder rebinds every properties bean after startup:
     * the bound values must survive it.
     */
    @Test
    fun propertiesSurviveRebind() {
        contextRunner
            .withPropertyValues(
                "spring.application.name=app",
                "${CoSkyRegistryProperties.PREFIX}.weight=100",
                "${CoSkyRegistryProperties.PREFIX}.initial-status=OUT_OF_SERVICE",
            )
            .withUserConfiguration(
                DataRedisAutoConfiguration::class.java,
                DataRedisReactiveAutoConfiguration::class.java,
                UtilAutoConfiguration::class.java,
                AutoServiceRegistrationConfiguration::class.java,
                ConfigurationPropertiesRebinderAutoConfiguration::class.java,
                CoSkyAutoConfiguration::class.java,
                CoSkyAutoServiceRegistrationAutoConfiguration::class.java,
            )
            .run {
                it.getBean(ConfigurationPropertiesRebinder::class.java).rebind()
                val properties = it.getBean(CoSkyRegistryProperties::class.java)
                properties.weight.assert().isEqualTo(100)
                properties.initialStatus.assert().isEqualTo("OUT_OF_SERVICE")
                it.getBean(CoSkyRegistration::class.java).metadata[StatusConstants.INSTANCE_STATUS_KEY]
                    .assert().isEqualTo("OUT_OF_SERVICE")
            }
    }
}
