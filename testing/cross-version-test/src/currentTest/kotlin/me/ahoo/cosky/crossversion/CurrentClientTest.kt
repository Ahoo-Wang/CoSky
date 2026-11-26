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

import io.grpc.ClientInterceptors
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.stub.MetadataUtils
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.Endpoint
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.ListRequest
import me.ahoo.cosky.api.v1.PutRequest
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceServiceGrpcKt
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.namespaceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.client.CoSkyClient
import me.ahoo.cosky.client.CoSkyClientOptions
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * This tree's client against the release's server, as an application upgraded before the servers are: it reads a
 * Config and follows it, registers and discovers, and carries on once the server restarted. It sticks to what the
 * release offers: a newer client may rely on what only a newer server has, which is no break.
 */
class CurrentClientTest {
    private val clients = mutableListOf<CoSkyClient>()
    private val namespace = "current-${UUID.randomUUID().toString().take(8)}"

    private fun newClient(): CoSkyClient = CoSkyClient(
        CoSkyClientOptions(listOf(server.endpoint)) {
            token = ADMIN
            snapshotDir = null
            reconnectMin = 20.milliseconds
            reconnectMax = 500.milliseconds
            reRegisterJitter = Duration.ZERO
            leaseTtl = LEASE_TTL
        },
    ).also { clients += it }

    private fun config(content: String): Resource = resource {
        meta = objectMeta {
            namespace = this@CurrentClientTest.namespace
            name = "app.yaml"
        }
        config = configSpec {
            format = ConfigFormat.CONFIG_FORMAT_YAML
            this.content = content
        }
    }

    private suspend fun put(resource: Resource) {
        resources.put(PutRequest.newBuilder().setResource(resource).build())
    }

    private suspend fun instancesOnServer(): List<Resource> = resources.list(
        ListRequest.newBuilder().setNamespace(namespace).setKind(Kind.KIND_INSTANCE).build(),
    ).itemsList

    @AfterEach
    fun closeClients() {
        clients.forEach { it.close() }
    }

    @Test
    fun `this client reads, follows, registers and discovers on the release's server, across its restart`() =
        runBlocking<Unit>(virtualThreads) {
            withTimeout(3.minutes) {
                put(
                    resource {
                        meta = objectMeta {
                            namespace = "_system"
                            name = this@CurrentClientTest.namespace
                        }
                        this.namespace = namespaceSpec {}
                    },
                )
                put(config("greeting: hello"))
                val application = newClient().namespace(namespace)
                application.configs.load("app.yaml")?.config?.content.assert().isEqualTo("greeting: hello")
                val view = application.configs.watch("app.yaml")
                put(config("greeting: bonjour"))
                view.first { it?.config?.content == "greeting: bonjour" }

                val registration = application.instances.register("order", ENDPOINT, instanceName = "order-1")
                val consumer = newClient().namespace(namespace)
                val discovered = consumer.instances.watch("order")
                discovered.first { list -> list.map { it.meta.name } == listOf("order-1") }
                consumer.instances.services().assert().isEqualTo(setOf("order"))
                val lease = instancesOnServer().single().meta.lease

                runInterruptible { server.restart() }
                put(config("greeting: hi"))
                view.first { it?.config?.content == "greeting: hi" }
                // Renewed after the restart for longer than a lease left alone lasts, so not registered again.
                delay(PAST_REAPING)
                instancesOnServer().single().meta.lease.assert().isEqualTo(lease)

                registration.close()
                discovered.first { it.isEmpty() }
            }
        }

    companion object {
        private val ADMIN = CoSkyServer.newToken()
        private val LEASE_TTL = 5.seconds
        private val PAST_REAPING = 8.seconds
        private val ENDPOINT = Endpoint.newBuilder().setScheme("http").setHost("10.0.0.1").setPort(8080).build()
        private val virtualThreads = Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()
        private val REDIS = Redis()
        private val server = BaselineServer(REDIS, ADMIN).also { it.start() }
        private val channel = ManagedChannelBuilder.forTarget(server.endpoint).usePlaintext().build()
        private val resources = ResourceServiceGrpcKt.ResourceServiceCoroutineStub(
            ClientInterceptors.intercept(
                channel,
                MetadataUtils.newAttachHeadersInterceptor(
                    Metadata().apply {
                        put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer $ADMIN")
                    },
                ),
            ),
        ).withWaitForReady()

        @AfterAll
        @JvmStatic
        fun stop() {
            channel.shutdownNow()
            server.close()
            REDIS.close()
        }
    }
}
