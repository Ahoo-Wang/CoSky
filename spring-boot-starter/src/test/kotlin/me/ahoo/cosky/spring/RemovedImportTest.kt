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

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.client.CoSkyClient
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.context.refresh.ContextRefresher
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A Config only another Config's `spring.config.import` named leaves the application once no import names it, though
 * a refresh only adds and replaces property sources (design §7.7).
 */
class RemovedImportTest {
    private val server = TestCoSkyServer()
    private val contexts = mutableListOf<ConfigurableApplicationContext>()

    @ConfigurationProperties("child")
    class ChildProperties {
        var value: String? = null
    }

    /** Counts the refreshes that have finished, the reconciliation included, which listens first. */
    class Refreshes : ApplicationListener<RefreshScopeRefreshedEvent> {
        val count = AtomicInteger()

        override fun onApplicationEvent(event: RefreshScopeRefreshedEvent) {
            count.incrementAndGet()
        }
    }

    @EnableAutoConfiguration
    @EnableConfigurationProperties(ChildProperties::class)
    class Application {
        @Bean
        fun refreshes() = Refreshes()
    }

    private fun application(vararg arguments: String): ConfigurableApplicationContext =
        SpringApplicationBuilder(Application::class.java).run(
            "--spring.application.name=order",
            "--spring.grpc.server.enabled=false",
            "--spring.main.web-application-type=none",
            "--spring.cloud.compatibility-verifier.enabled=false",
            "--cosky.snapshot.enabled=false",
            "--cosky.endpoints=${server.endpoint}",
            "--cosky.namespace=${TestCoSkyServer.NAMESPACE}",
            *arguments,
        ).also { contexts += it }

    @AfterEach
    fun stop() {
        contexts.forEach { it.close() }
        server.close()
    }

    private suspend fun eventually(condition: () -> Boolean) {
        withTimeout(15.seconds) {
            while (!condition()) {
                delay(20.milliseconds)
            }
        }
    }

    /** Makes a change, and waits for the refresh it asks for to finish. */
    private suspend fun refreshedAfter(context: ConfigurableApplicationContext, change: () -> Unit) {
        val refreshes = context.getBean(Refreshes::class.java).count
        val before = refreshes.get()
        change()
        eventually { refreshes.get() > before }
    }

    @Test
    fun `a config whose import is removed leaves, on the refresh the change asks for`() = runBlocking<Unit> {
        server.put(TestCoSkyServer.config("parent.yaml", "parent: before\nspring.config.import: cosky:child.yaml"))
        server.put(TestCoSkyServer.config("child.yaml", "child:\n  value: gone"))
        val context = application("--spring.config.import=cosky:parent.yaml")
        context.getBean(ChildProperties::class.java).value.assert().isEqualTo("gone")

        // child.yaml stays on the server, for whoever else imports it.
        refreshedAfter(context) { server.put(TestCoSkyServer.config("parent.yaml", "parent: after")) }

        context.environment.getProperty("parent").assert().isEqualTo("after")
        context.environment.getProperty("child.value").assert().isNull()
        context.environment.propertySources.map { it.name }.assert().doesNotContain("cosky:child.yaml")
        context.getBean(ChildProperties::class.java).value.assert().isNull()
    }

    @Test
    fun `a config whose import is removed leaves, on a refresh the application makes itself`() = runBlocking<Unit> {
        server.put(TestCoSkyServer.config("parent.yaml", "parent: before\nspring.config.import: cosky:child.yaml"))
        server.put(TestCoSkyServer.config("child.yaml", "child:\n  value: gone"))
        val context = application("--spring.config.import=cosky:parent.yaml", "--cosky.config.refresh.enabled=false")
        server.put(TestCoSkyServer.config("parent.yaml", "parent: after"))
        val parent = context.getBean(CoSkyClient::class.java).namespace(TestCoSkyServer.NAMESPACE).configs
            .watch("parent.yaml")
        eventually { parent.value?.config?.content == "parent: after" }

        context.getBean(ContextRefresher::class.java).refresh()

        context.environment.getProperty("parent").assert().isEqualTo("after")
        context.environment.getProperty("child.value").assert().isNull()
        context.getBean(ChildProperties::class.java).value.assert().isNull()
    }

    @Test
    fun `a config two imports name stays until neither does, and comes back with an import`() = runBlocking<Unit> {
        server.put(TestCoSkyServer.config("a.yaml", "a: 1\nspring.config.import: cosky:shared.yaml"))
        server.put(TestCoSkyServer.config("b.yaml", "b: 1\nspring.config.import: cosky:shared.yaml"))
        server.put(TestCoSkyServer.config("shared.yaml", "shared: here"))
        val context = application("--spring.config.import=cosky:a.yaml,cosky:b.yaml")

        refreshedAfter(context) { server.put(TestCoSkyServer.config("a.yaml", "a: 2")) }
        context.environment.getProperty("a").assert().isEqualTo("2")
        context.environment.getProperty("shared").assert().isEqualTo("here")

        refreshedAfter(context) { server.put(TestCoSkyServer.config("b.yaml", "b: 2")) }
        context.environment.getProperty("b").assert().isEqualTo("2")
        context.environment.getProperty("shared").assert().isNull()

        // Back, and followed again.
        refreshedAfter(context) {
            server.put(TestCoSkyServer.config("a.yaml", "a: 3\nspring.config.import: cosky:shared.yaml"))
        }
        context.environment.getProperty("shared").assert().isEqualTo("here")
        refreshedAfter(context) { server.put(TestCoSkyServer.config("shared.yaml", "shared: changed")) }
        context.environment.getProperty("shared").assert().isEqualTo("changed")
    }
}
