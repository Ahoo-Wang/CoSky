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

import io.grpc.Server
import io.grpc.ServerInterceptors
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.endpoint
import me.ahoo.cosky.api.v1.instanceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.server.adapter.grpc.ClientNameInterceptor
import me.ahoo.cosky.server.adapter.grpc.LeaseGrpcService
import me.ahoo.cosky.server.adapter.grpc.ResourceGrpcService
import me.ahoo.cosky.server.adapter.grpc.WatchGrpcService
import me.ahoo.cosky.server.resource.ResourceService
import me.ahoo.cosky.server.view.TopologyRecorder
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.memory.MemoryStore
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import me.ahoo.cosky.server.CoSkyProperties as ServerProperties

/**
 * The real server code on a real port, so that the starter goes through the client's default transport.
 */
internal class TestCoSkyServer : AutoCloseable {
    val store = MemoryStore()
    val topology = TopologyRecorder(store, 1.minutes)
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)
    private val server: Server
    val endpoint: String

    init {
        val caches = WatchCache(store, WINDOW, scope)
        server = NettyServerBuilder.forAddress(InetSocketAddress("localhost", 0))
            .addService(ResourceGrpcService(ResourceService(store, caches)))
            .addService(LeaseGrpcService(store))
            .addService(
                ServerInterceptors.intercept(
                    WatchGrpcService(caches, ServerProperties(), topology = topology),
                    ClientNameInterceptor(),
                ),
            )
            .build()
            .start()
        endpoint = "localhost:${server.port}"
        runBlocking { store.createNamespace(NAMESPACE) }
    }

    fun put(resource: Resource): Unit = runBlocking {
        store.apply(NAMESPACE, listOf(Mutation.Put(resource)), "test")
    }

    fun instances(): List<Resource> = runBlocking {
        store.readAll(NAMESPACE).toList().filter { it.hasInstance() }
    }

    override fun close() {
        server.shutdownNow().awaitTermination(1, TimeUnit.SECONDS)
        scope.cancel()
    }

    companion object {
        const val NAMESPACE = "prod"
        private const val WINDOW = 1000

        fun config(name: String, content: String, format: ConfigFormat = ConfigFormat.CONFIG_FORMAT_YAML): Resource =
            resource {
                meta = objectMeta {
                    namespace = NAMESPACE
                    this.name = name
                }
                config = configSpec {
                    this.format = format
                    this.content = content
                }
            }

        fun instance(name: String, service: String, weight: Int = 1): Resource = resource {
            meta = objectMeta {
                namespace = NAMESPACE
                this.name = name
            }
            instance = instanceSpec {
                this.service = service
                this.weight = weight
                endpoint = endpoint {
                    scheme = "http"
                    host = "10.0.0.1"
                    port = 8080
                }
                metadata["zone"] = "az1"
            }
        }
    }
}
