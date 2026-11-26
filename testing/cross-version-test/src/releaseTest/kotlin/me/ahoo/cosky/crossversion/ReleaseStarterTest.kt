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

package me.ahoo.cosky.crossversion

import kotlinx.coroutines.runInterruptible
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.cloud.client.discovery.DiscoveryClient

/**
 * A Spring Boot application on the release's starter, against this tree's server: it imports a Config and refreshes
 * when it changes, registers when its web server starts and leaves when it stops, discovers what others registered, and
 * carries on once the server restarted.
 */
class ReleaseStarterTest {
    @EnableAutoConfiguration
    class Application

    @Test
    fun `an application on the release's starter imports, refreshes, registers and discovers`() = scenario {
        val ns = uniqueName("starter")
        calls.put(namespace(ns))
        calls.put(config(ns, "order.yaml", "order:\n  limit: 10", ConfigFormat.CONFIG_FORMAT_YAML))
        calls.put(instance(ns, "payment-1", "payment"))
        suspend fun instances(): List<Resource> =
            calls.list(ns, Kind.KIND_INSTANCE).filter { it.instance.service == "order" }

        val context = runInterruptible {
            SpringApplicationBuilder(Application::class.java).run(
                "--spring.application.name=order",
                "--spring.main.web-application-type=reactive",
                "--server.port=0",
                "--spring.config.import=cosky:order.yaml",
                "--cosky.endpoints=${server.endpoint}",
                "--cosky.namespace=$ns",
                "--cosky.token=$ADMIN",
                "--cosky.snapshot.enabled=false",
            )
        }
        context.use {
            val environment = context.environment
            environment.getProperty("order.limit").assert().isEqualTo("10")
            val port = environment.getRequiredProperty("local.server.port", Int::class.java)
            eventually { instances().singleOrNull()?.instance?.endpoint?.port == port }
            val discovery = context.getBean(DiscoveryClient::class.java)
            discovery.getInstances("payment").map { it.instanceId }.assert().containsExactly("payment-1")

            calls.put(config(ns, "order.yaml", "order:\n  limit: 20", ConfigFormat.CONFIG_FORMAT_YAML))
            eventually { environment.getProperty("order.limit") == "20" }

            val lease = instances().single().meta.lease
            runInterruptible { server.restart() }
            calls.put(config(ns, "order.yaml", "order:\n  limit: 30", ConfigFormat.CONFIG_FORMAT_YAML))
            eventually { environment.getProperty("order.limit") == "30" }
            calls.put(instance(ns, "payment-2", "payment"))
            eventually { discovery.getInstances("payment").map { it.instanceId } == listOf("payment-1", "payment-2") }
            // Kept across the restart, not registered again.
            instances().single().meta.lease.assert().isEqualTo(lease)
        }
        eventually { instances().isEmpty() }
    }

    companion object {
        private val ADMIN = CoSkyServer.newToken()
        private val REDIS = Redis()
        private val server = CurrentServer(REDIS.uri, ADMIN).also { it.start() }
        private val calls = Calls(server, ADMIN)

        @AfterAll
        @JvmStatic
        fun stop() {
            calls.close()
            server.close()
            REDIS.close()
        }
    }
}
