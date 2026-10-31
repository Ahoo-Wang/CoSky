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

package me.ahoo.cosky.test.conformance

import io.grpc.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.DeleteRequest
import me.ahoo.cosky.api.v1.Endpoint
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.LeaseServiceGrpcKt
import me.ahoo.cosky.api.v1.ListRequest
import me.ahoo.cosky.api.v1.PutRequest
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.ResourceServiceGrpcKt
import me.ahoo.cosky.api.v1.RevokeRequest
import me.ahoo.cosky.api.v1.WatchRequest
import me.ahoo.cosky.api.v1.WatchServiceGrpcKt
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.namespaceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.client.CoSkyClient
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * What a client can rely on from a server, whatever it stores on (design §7.11): the client drives a real server, so
 * this tests the protocol from the far end. Extend it per store.
 */
abstract class ProtocolConformanceTest {
    /**
     * A channel to the server, for the writes of the other party; authenticated if the server needs that.
     */
    protected abstract val channel: Channel

    /**
     * A client of the server, closed by the implementation.
     */
    protected abstract fun newClient(): CoSkyClient

    /**
     * Stops the server and starts it again on the same address and data.
     */
    protected abstract suspend fun restartServer()

    /**
     * Loses the writes after [revision] of [namespace], as a failover can.
     */
    protected abstract suspend fun loseWritesAfter(namespace: String, revision: Long)

    private val namespace = "protocol-${UUID.randomUUID().toString().take(8)}"
    private val resources by lazy { ResourceServiceGrpcKt.ResourceServiceCoroutineStub(channel).withWaitForReady() }
    private val leases by lazy { LeaseServiceGrpcKt.LeaseServiceCoroutineStub(channel).withWaitForReady() }
    private val watches by lazy { WatchServiceGrpcKt.WatchServiceCoroutineStub(channel).withWaitForReady() }

    private fun scenario(block: suspend CoroutineScope.() -> Unit): Unit = runBlocking {
        withTimeout(SCENARIO_TIMEOUT) {
            resources.put(
                PutRequest.newBuilder().setResource(
                    resource {
                        meta = objectMeta {
                            namespace = NamingRules.SYSTEM_NAMESPACE
                            name = this@ProtocolConformanceTest.namespace
                        }
                        this.namespace = namespaceSpec {}
                    },
                ).build(),
            )
            block()
        }
    }

    private fun config(name: String, content: String = "a"): Resource = resource {
        meta = objectMeta {
            namespace = this@ProtocolConformanceTest.namespace
            this.name = name
        }
        config = configSpec {
            format = ConfigFormat.CONFIG_FORMAT_TEXT
            this.content = content
        }
    }

    private suspend fun put(resource: Resource): Long =
        resources.put(PutRequest.newBuilder().setResource(resource).build()).revision

    private fun instanceKey(name: String): ResourceKey = ResourceKey.newBuilder()
        .setNamespace(namespace)
        .setKind(Kind.KIND_INSTANCE)
        .setName(name)
        .build()

    private suspend fun instancesOnServer(): List<Resource> = resources.list(
        ListRequest.newBuilder().setNamespace(namespace).setKind(Kind.KIND_INSTANCE).build(),
    ).itemsList

    private suspend fun eventually(condition: suspend () -> Boolean) {
        while (!condition()) {
            delay(POLL)
        }
    }

    @Test
    fun `a view starts from what exists, then follows`() = scenario {
        put(config("a", "first"))
        val view = newClient().namespace(namespace).configs.watch("a")
        view.first { it?.config?.content == "first" }
        put(config("a", "second"))
        view.first { it?.config?.content == "second" }
    }

    @Test
    fun `a deleted object leaves the view`() = scenario {
        put(config("a"))
        val view = newClient().namespace(namespace).configs.watch("a")
        view.first { it != null }
        resources.delete(DeleteRequest.newBuilder().setKey(config("a").keyOf()).build())
        view.first { it == null }
    }

    @Test
    fun `loading tells missing from not known yet`() = scenario {
        val configs = newClient().namespace(namespace).configs
        configs.load("missing").assert().isNull()
        put(config("present"))
        eventually { configs.load("present") != null }
    }

    @Test
    fun `what is larger than a message arrives whole, in parts`() = scenario {
        // Past the 2 MiB of one message, and past the 4 MiB gRPC allows by default.
        val large = "x".repeat(LARGE_CONFIG_BYTES)
        var revision = 0L
        repeat(LARGE_CONFIG_COUNT) { revision = put(config("c$it", large)) }

        val requests = flow {
            emit(
                WatchRequest.newBuilder().apply {
                    createBuilder.setWatchId(1).setNamespace(namespace).setKind(Kind.KIND_CONFIG)
                }.build(),
            )
            awaitCancellation()
        }
        val chunks = watches.watch(requests).filter { it.hasSnapshot() }.transformWhile {
            emit(it.snapshot)
            it.snapshot.more
        }.toList()
        chunks.sumOf { it.itemsCount }.assert().isEqualTo(LARGE_CONFIG_COUNT)
        (chunks.size > 1).assert().isTrue()

        val listed = mutableListOf<Resource>()
        var token = ""
        do {
            val page = resources.list(
                ListRequest.newBuilder()
                    .setNamespace(namespace)
                    .setKind(Kind.KIND_CONFIG)
                    .setMinRevision(revision)
                    .setPageToken(token)
                    .build(),
            )
            listed += page.itemsList
            token = page.nextPageToken
        } while (token.isNotEmpty())
        listed.map { it.meta.name }.assert().isEqualTo(List(LARGE_CONFIG_COUNT) { "c$it" })
    }

    @Test
    fun `a registration is seen by watchers and goes when it closes`() = scenario {
        val registering = newClient().namespace(namespace).instances
        val watching = newClient().namespace(namespace).instances.watch("order")
        val registration = registering.register("order", ENDPOINT, instanceName = "order-1")
        watching.first { it.singleOrNull()?.meta?.name == "order-1" }
        registration.close()
        watching.first { it.isEmpty() }
    }

    @Test
    fun `services lists what has instances`() = scenario {
        val instances = newClient().namespace(namespace).instances
        instances.register("order", ENDPOINT)
        instances.register("payment", ENDPOINT)
        eventually { instances.services() == setOf("order", "payment") }
    }

    @Test
    fun `a revoked lease is restored with its registrations`() = scenario {
        val instances = newClient().namespace(namespace).instances
        instances.register("order", ENDPOINT, instanceName = "order-1")
        val lease = instancesOnServer().single().meta.lease
        leases.revoke(RevokeRequest.newBuilder().setNamespace(namespace).setLease(lease).build())
        eventually { instancesOnServer().singleOrNull()?.meta?.lease?.let { it != lease } == true }
    }

    @Test
    fun `an instance lost behind a live lease is put back`() = scenario {
        val instances = newClient().namespace(namespace).instances
        instances.register("order", ENDPOINT, instanceName = "order-1")
        val lease = instancesOnServer().single().meta.lease
        resources.delete(DeleteRequest.newBuilder().setKey(instanceKey("order-1")).build())
        eventually { instancesOnServer().singleOrNull()?.meta?.lease == lease }
    }

    @Test
    fun `a view resumes across a server restart without missing anything`() = scenario {
        put(config("a", "first"))
        val view = newClient().namespace(namespace).configs.watch("a")
        view.first { it?.config?.content == "first" }
        restartServer()
        put(config("a", "second"))
        view.first { it?.config?.content == "second" }
    }

    @Test
    fun `lost writes reach a view as a new timeline, which it follows`() = scenario {
        val first = put(config("a", "kept"))
        val view = newClient().namespace(namespace).configs.watch("a")
        view.first { it?.config?.content == "kept" }
        put(config("a", "lost"))
        view.first { it?.config?.content == "lost" }
        loseWritesAfter(namespace, first)
        put(config("a", "after"))
        view.first { it?.config?.content == "after" }
    }

    private fun Resource.keyOf(): ResourceKey = ResourceKey.newBuilder()
        .setNamespace(meta.namespace)
        .setKind(Kind.KIND_CONFIG)
        .setName(meta.name)
        .build()

    private companion object {
        val SCENARIO_TIMEOUT = 60.seconds
        val POLL = 50.milliseconds
        const val LARGE_CONFIG_BYTES = 900 * 1024
        const val LARGE_CONFIG_COUNT = 6
        val ENDPOINT: Endpoint = Endpoint.newBuilder().setScheme("http").setHost("localhost").setPort(8080).build()
    }
}
