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

package me.ahoo.cosky.server

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.LeaseServiceGrpcKt
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceServiceGrpcKt
import me.ahoo.cosky.api.v1.WatchRequest
import me.ahoo.cosky.api.v1.WatchResponse
import me.ahoo.cosky.api.v1.WatchServiceGrpcKt
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.endpoint
import me.ahoo.cosky.api.v1.instanceSpec
import me.ahoo.cosky.api.v1.namespaceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.putRequest
import me.ahoo.cosky.api.v1.resource
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.containers.GenericContainer
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList

internal class RedisContainer(vararg command: String) : GenericContainer<RedisContainer>("redis:7.4-alpine") {
    init {
        withExposedPorts(REDIS_PORT)
        if (command.isNotEmpty()) {
            withCommand(*command)
        }
    }

    companion object {
        const val REDIS_PORT = 6379
    }
}

/**
 * A real server on a real port, and a real client, as nothing but the protocol connects them (design §0).
 */
internal class RunningServer(properties: Map<String, Any>, vararg sources: Class<*>) : AutoCloseable {
    private val port = ServerSocket(0).use { it.localPort }
    private val context: ConfigurableApplicationContext =
        SpringApplicationBuilder(CoSkyServerApplication::class.java, *sources).run(
            // As arguments: SpringApplicationBuilder.properties only sets defaults, which application.yaml overrides.
            *(properties + ("spring.grpc.server.port" to port)).map { (key, value) -> "--$key=$value" }.toTypedArray(),
        )
    private val channel: ManagedChannel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build()

    val resources = ResourceServiceGrpcKt.ResourceServiceCoroutineStub(channel)
    val leases = LeaseServiceGrpcKt.LeaseServiceCoroutineStub(channel)
    val watches = WatchServiceGrpcKt.WatchServiceCoroutineStub(channel)

    suspend fun createNamespace(name: String) {
        resources.put(
            putRequest {
                resource = resource {
                    meta = objectMeta {
                        namespace = "_system"
                        this.name = name
                    }
                    namespace = namespaceSpec {}
                }
            },
        )
    }

    suspend fun putConfig(ns: String, name: String, value: String) {
        resources.put(putRequest { resource = config(ns, name, value) })
    }

    suspend fun putInstance(ns: String, name: String, lease: Long) {
        resources.put(putRequest { resource = instance(ns, name, lease) })
    }

    /**
     * Opens one watch, recording what arrives in [seen].
     */
    fun watch(
        ns: String,
        kind: Kind,
        seen: MutableList<WatchResponse> = CopyOnWriteArrayList(),
        fromRevision: Long = 0,
        fromEpoch: String = "",
    ): Flow<WatchResponse> {
        val requests = flow<WatchRequest> {
            emit(
                WatchRequest.newBuilder().setCreate(
                    WatchRequest.Create.newBuilder()
                        .setWatchId(1)
                        .setNamespace(ns)
                        .setKind(kind)
                        .setFromRevision(fromRevision)
                        .setFromEpoch(fromEpoch),
                ).build(),
            )
            // The request stream stays open for as long as the watch does.
            awaitCancellation()
        }
        return watches.watch(requests).onEach { seen += it }
    }

    override fun close() {
        channel.shutdownNow()
        context.close()
    }

    companion object {
        fun config(ns: String, name: String, value: String): Resource = resource {
            meta = objectMeta {
                namespace = ns
                this.name = name
            }
            config = configSpec {
                format = ConfigFormat.CONFIG_FORMAT_TEXT
                content = value
            }
        }

        fun instance(ns: String, name: String, lease: Long): Resource = resource {
            meta = objectMeta {
                namespace = ns
                this.name = name
                this.lease = lease
            }
            instance = instanceSpec {
                service = "order"
                endpoint = endpoint {
                    scheme = "http"
                    host = "10.0.0.1"
                    port = 8080
                }
            }
        }
    }
}
