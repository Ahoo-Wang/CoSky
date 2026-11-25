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
import org.springframework.cloud.context.refresh.ContextRefresher
import org.springframework.context.ConfigurableApplicationContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A required Config that goes away while the application runs keeps its values, and leaves the other imports to
 * refresh as before (design §7.7). Only at startup is it an error.
 */
class RequiredConfigRefreshTest {
    private val server = TestCoSkyServer()
    private val contexts = mutableListOf<ConfigurableApplicationContext>()

    @EnableAutoConfiguration
    class Application

    private fun application(vararg arguments: String): ConfigurableApplicationContext =
        SpringApplicationBuilder(Application::class.java).run(
            "--spring.application.name=order",
            "--spring.grpc.server.enabled=false",
            "--spring.main.web-application-type=none",
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

    @Test
    fun `a required config that goes away does not hold up the refreshes of the other imports`() = runBlocking<Unit> {
        server.put(TestCoSkyServer.config("db.yaml", "db:\n  url: jdbc:before"))
        server.put(TestCoSkyServer.config("feature.yaml", "feature:\n  enabled: false"))
        val context = application("--spring.config.import=cosky:db.yaml,cosky:feature.yaml")

        server.delete("db.yaml")
        server.put(TestCoSkyServer.config("feature.yaml", "feature:\n  enabled: true"))

        eventually { context.environment.getProperty("feature.enabled") == "true" }
        context.environment.getProperty("db.url").assert().isEqualTo("jdbc:before")
    }

    @Test
    fun `a required config keeps what it held through every refresh while it is away`() = runBlocking<Unit> {
        server.put(TestCoSkyServer.config("db.yaml", "db:\n  url: jdbc:before\n---\ndb:\n  pool: 4"))
        server.put(TestCoSkyServer.config("feature.yaml", "feature:\n  enabled: false"))
        // Refreshing by hand, to know which refresh saw what.
        val context = application(
            "--cosky.config.refresh.enabled=false",
            "--spring.config.import=cosky:db.yaml,cosky:feature.yaml",
        )
        val configs = context.getBean(CoSkyClient::class.java).namespace(TestCoSkyServer.NAMESPACE).configs
        val refresher = context.getBean(ContextRefresher::class.java)

        server.delete("db.yaml")
        eventually { configs.watch("db.yaml").value == null }
        listOf("true", "false").forEach { enabled ->
            server.put(TestCoSkyServer.config("feature.yaml", "feature:\n  enabled: $enabled"))
            eventually { configs.watch("feature.yaml").value?.config?.content == "feature:\n  enabled: $enabled" }
            refresher.refresh()
            context.environment.getProperty("feature.enabled").assert().isEqualTo(enabled)
            context.environment.getProperty("db.url").assert().isEqualTo("jdbc:before")
            context.environment.getProperty("db.pool").assert().isEqualTo("4")
        }

        server.put(TestCoSkyServer.config("db.yaml", "db:\n  url: jdbc:after"))
        eventually { configs.watch("db.yaml").value != null }
        refresher.refresh()
        context.environment.getProperty("db.url").assert().isEqualTo("jdbc:after")
        context.environment.getProperty("db.pool").assert().isNull()
    }
}
