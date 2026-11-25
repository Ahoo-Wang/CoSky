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

package me.ahoo.cosky.server.adapter.grpc

import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.Server
import io.grpc.ServerInterceptors
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.MetadataUtils
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.v1.GetRequest
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceServiceGrpcKt
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.audit.AuditLogger
import me.ahoo.cosky.server.auth.AccessControl
import me.ahoo.cosky.server.auth.Tokens
import me.ahoo.cosky.server.observe.CoSkyMetrics
import me.ahoo.cosky.server.resource.ResourceService
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * `cosky_auth_failures_total` tells a call without a token from one with a bad token, and both from a server that
 * could not decide, which no client can do anything about (design §7.9).
 */
class AuthFailuresTest {
    private val store = MemoryStore()
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)
    private val registry = SimpleMeterRegistry()
    private val servers = mutableListOf<Server>()
    private val channels = mutableListOf<ManagedChannel>()

    @AfterEach
    fun stop() {
        channels.forEach { it.shutdownNow() }
        servers.forEach { it.shutdownNow() }
        scope.cancel()
    }

    /**
     * A server on [store], called with [token].
     */
    private suspend fun call(store: Store, token: String?, loaded: Boolean = true): Status.Code {
        val caches = WatchCache(store, WINDOW, scope)
        if (loaded) {
            caches.of(NamingRules.SYSTEM_NAMESPACE).views.first { it != null }
        }
        val access = AccessControl(caches)
        val name = UUID.randomUUID().toString()
        val service = ResourceGrpcService(ResourceService(store, caches, authorizer = access))
        val interceptor = AuthInterceptor(access, AuditLogger(), CoSkyMetrics(registry))
        servers += InProcessServerBuilder.forName(name)
            .addService(ServerInterceptors.intercept(service, interceptor))
            .build()
            .start()
        val channel = InProcessChannelBuilder.forName(name)
            .also { builder ->
                token?.let {
                    val headers = Metadata().apply { put(AUTHORIZATION, "Bearer $it") }
                    builder.intercept(MetadataUtils.newAttachHeadersInterceptor(headers))
                }
            }
            .build()
            .also { channels += it }
        val failure = runCatching {
            ResourceServiceGrpcKt.ResourceServiceCoroutineStub(channel).get(GetRequest.getDefaultInstance())
        }.exceptionOrNull()
        return (failure as StatusException).status.code
    }

    private fun failures(reason: String): Double =
        registry.get("cosky_auth_failures_total").tag("reason", reason).counter().count()

    @Test
    fun `a failure counts as missing, invalid or unavailable`() = runBlocking<Unit> {
        store.createNamespace(NamingRules.SYSTEM_NAMESPACE)
        call(store, null).assert().isEqualTo(Status.Code.UNAUTHENTICATED)
        call(store, Tokens.generate().plaintext).assert().isEqualTo(Status.Code.UNAUTHENTICATED)
        call(store, "not-a-token").assert().isEqualTo(Status.Code.UNAUTHENTICATED)
        // The access rules never finish loading.
        val loading = object : Store by store {
            override fun readAll(namespace: String): Flow<Resource> = flow { awaitCancellation() }
        }
        call(loading, Tokens.generate().plaintext, loaded = false).assert().isEqualTo(Status.Code.UNAVAILABLE)

        failures("missing").assert().isEqualTo(1.0)
        failures("invalid").assert().isEqualTo(2.0)
        failures("unavailable").assert().isEqualTo(1.0)
    }

    private companion object {
        const val WINDOW = 64
        val AUTHORIZATION: Metadata.Key<String> = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
    }
}
