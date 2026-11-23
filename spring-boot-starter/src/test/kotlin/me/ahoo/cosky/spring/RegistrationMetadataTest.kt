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
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.cloud.client.discovery.event.InstancePreRegisteredEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A listener of `InstancePreRegisteredEvent` completes the registration's metadata before it is made, as Spring
 * Cloud's other registries allow.
 */
class RegistrationMetadataTest {
    private val server = TestCoSkyServer()
    private val contexts = mutableListOf<ConfigurableApplicationContext>()

    @EnableAutoConfiguration
    class Application

    /** Adds an entry before the registration is made, as a listener knowing something only then does. */
    private val addsInternalPort = ApplicationListener<InstancePreRegisteredEvent> {
        checkNotNull(it.registration.metadata)["internal-port"] = "9999"
    }

    private fun application(vararg arguments: String): ConfigurableApplicationContext {
        val common = listOf(
            "--spring.application.name=order",
            "--spring.grpc.server.enabled=false",
            "--spring.main.web-application-type=reactive",
            "--server.port=0",
            "--cosky.snapshot.enabled=false",
            "--cosky.endpoints=${server.endpoint}",
            "--cosky.namespace=${TestCoSkyServer.NAMESPACE}",
        )
        return SpringApplicationBuilder(Application::class.java)
            .listeners(addsInternalPort)
            .run(*(common + arguments).toTypedArray())
            .also { contexts += it }
    }

    @AfterEach
    fun stop() {
        contexts.forEach { it.close() }
        server.close()
    }

    private suspend fun eventually(timeout: Duration = 15.seconds, condition: () -> Boolean) {
        withTimeout(timeout) {
            while (!condition()) {
                delay(20.milliseconds)
            }
        }
    }

    @Test
    fun `a listener adds to the metadata, with none configured`() = runBlocking<Unit> {
        val context = application()
        eventually { server.instances().isNotEmpty() }

        server.instances().single().instance.metadataMap.assert().containsEntry("internal-port", "9999")
        context.getBean(CoSkyProperties::class.java).registry.metadata.assert().isEmpty()
    }

    @Test
    fun `a listener adds to the configured metadata, which it leaves be`() = runBlocking<Unit> {
        val context = application("--cosky.registry.metadata.zone=az9")
        eventually { server.instances().isNotEmpty() }

        server.instances().single().instance.metadataMap.assert()
            .containsEntry("zone", "az9")
            .containsEntry("internal-port", "9999")
        context.getBean(CoSkyProperties::class.java).registry.metadata.assert().isEqualTo(mapOf("zone" to "az9"))
    }

    @Test
    fun `what a listener added is there again once a lost lease is restored`() = runBlocking<Unit> {
        application()
        eventually { server.instances().isNotEmpty() }
        val lost = server.instances().single().meta.lease
        server.store.expireLease(TestCoSkyServer.NAMESPACE, lost, "test", force = true)

        // Renewing, every third of the lease's 30 seconds, finds it lost and registers again within 5 more.
        eventually(30.seconds) { server.instances().singleOrNull()?.meta?.lease?.let { it != lost } == true }
        server.instances().single().instance.metadataMap.assert().containsEntry("internal-port", "9999")
    }
}
