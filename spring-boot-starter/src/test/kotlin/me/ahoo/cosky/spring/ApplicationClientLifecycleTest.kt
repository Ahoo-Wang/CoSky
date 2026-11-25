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
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.event.ApplicationPreparedEvent
import org.springframework.cloud.context.refresh.ContextRefresher
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * One client for each application, from its start to its end, however it starts and however it fails (design D41).
 */
class ApplicationClientLifecycleTest {
    private val server = TestCoSkyServer()
    private val contexts = mutableListOf<ConfigurableApplicationContext>()

    @EnableAutoConfiguration
    class Application

    private fun application(
        vararg arguments: String,
        listener: ApplicationListener<*>? = null,
    ): ConfigurableApplicationContext = SpringApplicationBuilder(Application::class.java)
        .apply { listener?.let { listeners(it) } }
        .run(
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
    fun `a refresh that adds the first import loads through the client the application has`(
        @TempDir directory: Path,
    ) = runBlocking<Unit> {
        server.put(TestCoSkyServer.config("order.yaml", "order:\n  limit: 10"))
        val settings = directory.resolve("application.yaml").apply { writeText("plain: true") }
        val context = application("--spring.config.additional-location=file:$directory/")
        // From the auto-configuration, and connected, as it would be for discovery or registration.
        val application = context.getBean(CoSkyApplicationClient::class.java)
        application.client.namespace(TestCoSkyServer.NAMESPACE).configs.load("unrelated.yaml")
        server.connections.assert().hasSize(1)

        settings.writeText("spring.config.import: cosky:order.yaml")
        context.getBean(ContextRefresher::class.java).refresh()

        context.environment.getProperty("order.limit").assert().isEqualTo("10")
        application.importedBy(TestCoSkyServer.NAMESPACE).keys.assert().containsExactly("order.yaml")
        server.connections.assert().hasSize(1)
        // And it follows the Config, as for an import at startup.
        server.put(TestCoSkyServer.config("order.yaml", "order:\n  limit: 20"))
        eventually { context.environment.getProperty("order.limit") == "20" }
    }

    @Test
    fun `an application that fails after its context took the client over, before it refreshed, closes it`() =
        runBlocking<Unit> {
            val handedOver = AtomicBoolean()
            val refusing = object : ApplicationListener<ApplicationPreparedEvent> {
                override fun onApplicationEvent(event: ApplicationPreparedEvent) {
                    handedOver.set(event.applicationContext.beanFactory.containsSingleton("coSkyApplicationClient"))
                    error("refused")
                }
            }

            val failure = runCatching {
                application("--spring.config.import=optional:cosky:order.yaml", listener = refusing)
            }.exceptionOrNull()

            failure?.message.assert().isEqualTo("refused")
            handedOver.get().assert().isTrue()
            eventually { server.connections.isEmpty() }
        }
}
