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

package me.ahoo.cosky.server.adapter

import com.google.gson.JsonParser
import io.grpc.StatusException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.error.ErrorCodes
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.DeleteRequest
import me.ahoo.cosky.api.v1.ErrorReason
import me.ahoo.cosky.api.v1.GetRequest
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.ListRequest
import me.ahoo.cosky.api.v1.PutRequest
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.ResourceServiceGrpcKt
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.server.CoSkyProperties
import me.ahoo.cosky.server.adapter.grpc.ResourceGrpcService
import me.ahoo.cosky.server.adapter.rest.RestRoutes
import me.ahoo.cosky.server.audit.AuditLogger
import me.ahoo.cosky.server.auth.Authorizer
import me.ahoo.cosky.server.auth.TokenService
import me.ahoo.cosky.server.resource.ResourceService
import me.ahoo.cosky.server.view.ViewService
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

/**
 * The same request through both protocols must end the same way (roadmap W3a): the same reason, with the gRPC code and
 * HTTP status of design §4.4.
 */
class ProtocolParityTest {
    private val store = MemoryStore()
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)
    private val caches = WatchCache(store, 64, scope)
    private val service = ResourceService(store, caches, minRevisionTimeout = 200.milliseconds)
    private val name = UUID.randomUUID().toString()
    private val server = InProcessServerBuilder.forName(name).addService(ResourceGrpcService(service)).build().start()
    private val channel = InProcessChannelBuilder.forName(name).build()
    private val grpc = ResourceServiceGrpcKt.ResourceServiceCoroutineStub(channel)
    private val rest = WebTestClient.bindToRouterFunction(
        RestRoutes(
            service,
            ViewService(store, caches),
            TokenService(store, caches),
            caches,
            Authorizer.ALLOW_ALL,
            null,
            AuditLogger(),
            CoSkyProperties(),
        ).routes(),
    ).build()

    @AfterEach
    fun stop() {
        channel.shutdownNow()
        server.shutdownNow()
        scope.cancel()
    }

    private fun config(configName: String = "a", namespace: String = NAMESPACE): Resource = resource {
        meta = objectMeta {
            this.namespace = namespace
            this.name = configName
        }
        config = configSpec {
            format = ConfigFormat.CONFIG_FORMAT_TEXT
            content = "a"
        }
    }

    private fun key(configName: String = "a", namespace: String = NAMESPACE): ResourceKey = ResourceKey.newBuilder()
        .setNamespace(namespace)
        .setKind(Kind.KIND_CONFIG)
        .setName(configName)
        .build()

    private val configBody = """{"config":{"format":"CONFIG_FORMAT_TEXT","content":"b"}}"""

    /**
     * @param grpcCall the request over gRPC.
     * @param restCall the same over REST.
     */
    private suspend fun sameFailure(
        expected: ErrorReason,
        grpcCall: suspend () -> Unit,
        restCall: () -> WebTestClient.ResponseSpec,
    ) {
        val failure = runCatching { grpcCall() }.exceptionOrNull() as StatusException
        CoSkyException.from(failure)?.reason.assert().isEqualTo(expected)
        val code = ErrorCodes.of(expected)
        failure.status.code.assert().isEqualTo(code.grpcCode)

        val response = restCall().expectStatus().isEqualTo(code.httpStatus)
            .expectBody(String::class.java).returnResult().responseBody
        val body = JsonParser.parseString(response).asJsonObject
        body["status"].asString.assert().isEqualTo(code.grpcCode.name)
        body.getAsJsonObject("detail")["reason"].asString.assert().isEqualTo(expected.name)
    }

    private fun scenario(block: suspend () -> Unit): Unit = runBlocking {
        store.createNamespace(NAMESPACE)
        block()
    }

    private fun restPut(uri: String, header: Pair<String, String>? = null) = rest.put().uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .also { spec -> header?.let { spec.header(it.first, it.second) } }
        .bodyValue(configBody)
        .exchange()

    @Test
    fun `a missing object`() = scenario {
        sameFailure(
            ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND,
            { grpc.get(GetRequest.newBuilder().setKey(key()).build()) },
            { rest.get().uri("/v1/namespaces/$NAMESPACE/configs/a").exchange() },
        )
    }

    @Test
    fun `a missing namespace`() = scenario {
        sameFailure(
            ErrorReason.ERROR_REASON_NAMESPACE_NOT_FOUND,
            { grpc.get(GetRequest.newBuilder().setKey(key(namespace = "missing")).build()) },
            { rest.get().uri("/v1/namespaces/missing/configs/a").exchange() },
        )
    }

    @Test
    fun `a write to a missing namespace`() = scenario {
        sameFailure(
            ErrorReason.ERROR_REASON_NAMESPACE_NOT_FOUND,
            { grpc.put(PutRequest.newBuilder().setResource(config(namespace = "missing")).build()) },
            { restPut("/v1/namespaces/missing/configs/a") },
        )
    }

    @Test
    fun `an invalid name`() = scenario {
        sameFailure(
            ErrorReason.ERROR_REASON_INVALID_ARGUMENT,
            { grpc.put(PutRequest.newBuilder().setResource(config("Bad Name!")).build()) },
            { restPut("/v1/namespaces/$NAMESPACE/configs/Bad Name!") },
        )
    }

    @Test
    fun `creating what exists`() = scenario {
        grpc.put(PutRequest.newBuilder().setResource(config()).build())
        sameFailure(
            ErrorReason.ERROR_REASON_REVISION_CONFLICT,
            { grpc.put(PutRequest.newBuilder().setResource(config()).setExpectedRevision(-1).build()) },
            { restPut("/v1/namespaces/$NAMESPACE/configs/a", "If-None-Match" to "*") },
        )
    }

    @Test
    fun `a stale revision`() = scenario {
        grpc.put(PutRequest.newBuilder().setResource(config()).build())
        sameFailure(
            ErrorReason.ERROR_REASON_REVISION_CONFLICT,
            { grpc.delete(DeleteRequest.newBuilder().setKey(key()).setExpectedRevision(99).build()) },
            { rest.delete().uri("/v1/namespaces/$NAMESPACE/configs/a").header("If-Match", "\"99\"").exchange() },
        )
    }

    @Test
    fun `a revision that does not arrive`() = scenario {
        grpc.put(PutRequest.newBuilder().setResource(config()).build())
        sameFailure(
            ErrorReason.ERROR_REASON_MIN_REVISION_TIMEOUT,
            { grpc.get(GetRequest.newBuilder().setKey(key()).setMinRevision(99).build()) },
            { rest.get().uri("/v1/namespaces/$NAMESPACE/configs/a?minRevision=99").exchange() },
        )
    }

    @Test
    fun `a bad page token`() = scenario {
        sameFailure(
            ErrorReason.ERROR_REASON_INVALID_ARGUMENT,
            {
                grpc.list(
                    ListRequest.newBuilder()
                        .setNamespace(NAMESPACE)
                        .setKind(Kind.KIND_CONFIG)
                        .setPageToken("nonsense")
                        .build(),
                )
            },
            { rest.get().uri("/v1/namespaces/$NAMESPACE/configs?pageToken=nonsense").exchange() },
        )
    }

    @Test
    fun `both read what either wrote`() = scenario {
        val revision = grpc.put(PutRequest.newBuilder().setResource(config()).build()).revision
        caches.of(NAMESPACE).views.first { it != null && it.revision >= revision }
        val overRest = rest.get().uri("/v1/namespaces/$NAMESPACE/configs/a").exchange()
            .expectStatus().isOk.expectBody(String::class.java).returnResult().responseBody
        val json = JsonParser.parseString(overRest).asJsonObject
        json.getAsJsonObject("meta")["revision"].asString.assert().isEqualTo(revision.toString())

        val written = restPut("/v1/namespaces/$NAMESPACE/configs/b").expectStatus().isOk
            .expectBody(String::class.java).returnResult().responseBody
        val writtenAt = JsonParser.parseString(written).asJsonObject["revision"].asLong
        grpc.get(GetRequest.newBuilder().setKey(key("b")).setMinRevision(writtenAt).build())
            .config.content.assert().isEqualTo("b")
    }

    private companion object {
        const val NAMESPACE = "parity"
    }
}
