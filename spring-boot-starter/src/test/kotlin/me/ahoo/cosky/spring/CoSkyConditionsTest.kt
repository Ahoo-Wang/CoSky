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

package me.ahoo.cosky.spring

import me.ahoo.cosky.spring.discovery.CoSkyDiscoveryClient
import me.ahoo.cosky.spring.discovery.CoSkyReactiveDiscoveryClient
import me.ahoo.cosky.spring.registry.CoSkyAutoServiceRegistration
import me.ahoo.cosky.spring.registry.CoSkyServiceRegistry
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.cloud.commons.util.UtilAutoConfiguration
import org.springframework.cloud.endpoint.event.RefreshEvent
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier

/**
 * Which parts of the starter apply, by the classpath, the kind of application and the switches.
 */
class CoSkyConditionsTest {
    private val server = TestCoSkyServer()

    private fun runner(): ApplicationContextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CoSkyAutoConfiguration::class.java))
        .withPropertyValues("cosky.endpoints=${server.endpoint}", "cosky.snapshot.enabled=false")

    @AfterEach
    fun stop() {
        server.close()
    }

    @Test
    fun `each integration applies only when its framework is there`() {
        runner()
            .withClassLoader(
                FilteredClassLoader(
                    RefreshEvent::class.java,
                    ServiceInstanceListSupplier::class.java,
                    HealthIndicator::class.java,
                    WebServerApplicationContext::class.java,
                ),
            )
            .run { context ->
                context.startupFailure.assert().isNull()
                context.containsBean("coSkyDiscoveryClient").assert().isTrue()
                context.containsBean("coSkyServiceRegistry").assert().isTrue()
                context.containsBean("coSkyConfigRefresher").assert().isFalse()
                context.containsBean("coSkyHealthIndicator").assert().isFalse()
                context.containsBean("coSkyAutoServiceRegistration").assert().isFalse()
            }
    }

    @Test
    fun `spring cloud's switches for discovery apply too`() {
        fun discovery(
            vararg switches: String,
            check: (AssertableApplicationContext) -> Unit
        ) = ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CoSkyAutoConfiguration::class.java))
            .withPropertyValues("cosky.endpoints=${server.endpoint}", "cosky.snapshot.enabled=false", *switches)
            .run(check)

        discovery("spring.cloud.discovery.enabled=false") { context ->
            context.getBeansOfType(CoSkyDiscoveryClient::class.java).assert().isEmpty()
            context.getBeansOfType(CoSkyReactiveDiscoveryClient::class.java).assert().isEmpty()
        }
        discovery("spring.cloud.discovery.blocking.enabled=false") { context ->
            context.getBeansOfType(CoSkyDiscoveryClient::class.java).assert().isEmpty()
            context.getBeansOfType(CoSkyReactiveDiscoveryClient::class.java).assert().hasSize(1)
        }
        discovery("spring.cloud.discovery.reactive.enabled=false") { context ->
            context.getBeansOfType(CoSkyDiscoveryClient::class.java).assert().hasSize(1)
            context.getBeansOfType(CoSkyReactiveDiscoveryClient::class.java).assert().isEmpty()
        }
    }

    @Test
    fun `an application without a web server registers nothing by itself, and needs no name for it`() {
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(UtilAutoConfiguration::class.java, CoSkyAutoConfiguration::class.java)
            )
            .withPropertyValues("cosky.endpoints=${server.endpoint}", "cosky.snapshot.enabled=false")
            .run { context ->
                context.startupFailure.assert().isNull()
                context.getBeansOfType(CoSkyAutoServiceRegistration::class.java).assert().isEmpty()
                context.getBeansOfType(CoSkyServiceRegistry::class.java).assert().hasSize(1)
            }
    }
}
