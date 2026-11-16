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

import me.ahoo.cosky.client.CoSkyClient
import me.ahoo.cosky.spring.registry.CoSkyRegistration
import me.ahoo.cosky.spring.registry.CoSkyServiceRegistry
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * Deregistering while the application goes on, as one that leaves its service but keeps its configuration does.
 */
class CoSkyServiceRegistryTest {
    private val server = TestCoSkyServer()
    private val properties = CoSkyProperties().apply {
        endpoints = listOf(server.endpoint)
        namespace = TestCoSkyServer.NAMESPACE
        snapshot.enabled = false
    }
    private val client = CoSkyClient(properties.toOptions())
    private val registry = CoSkyServiceRegistry(client, properties)
    private val registration = CoSkyRegistration("order", "localhost", false, emptyMap()).apply { setPort(PORT) }

    @AfterEach
    fun stop() {
        client.close()
        server.close()
    }

    @Test
    fun `a deregistration that fails says so, and can be tried again`() {
        registry.register(registration)
        server.instances().size.assert().isEqualTo(1)
        server.failingDeletes.set(1)

        val failure = runCatching { registry.deregister(registration) }.exceptionOrNull()

        failure.assert().isInstanceOf(IllegalStateException::class.java)
        failure?.message.assert().contains("order").contains("UNAVAILABLE").contains("again")
        server.instances().size.assert().isEqualTo(1)

        registry.deregister(registration)
        server.instances().assert().isEmpty()
    }

    @Test
    fun `deregistering what is not registered does nothing`() {
        registry.deregister(registration)
        registry.deregister(null)
        server.instances().assert().isEmpty()
    }

    private companion object {
        const val PORT = 8080
    }
}
