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
package me.ahoo.cosky.api

import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.GetRequest
import me.ahoo.cosky.api.v1.KeepAliveRequest
import me.ahoo.cosky.api.v1.KeepAliveResponse
import me.ahoo.cosky.api.v1.LeaseServiceGrpcKt
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceServiceGrpcKt
import me.ahoo.cosky.api.v1.getRequest
import me.ahoo.cosky.api.v1.keepAliveRequest
import me.ahoo.cosky.api.v1.keepAliveResponse
import me.ahoo.cosky.api.validation.Fixtures
import me.ahoo.cosky.api.validation.Validator
import me.ahoo.cosky.api.validation.requireValid
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The generated coroutine stubs work end to end, and error details survive the transport.
 */
class GeneratedStubsTest {
    private lateinit var server: Server
    private lateinit var channel: ManagedChannel

    private val resources = object : ResourceServiceGrpcKt.ResourceServiceCoroutineImplBase() {
        // A server converts CoSkyException itself; grpc-kotlin reports any other exception as UNKNOWN.
        override suspend fun get(request: GetRequest): Resource = try {
            requireValid(Validator.DEFAULT.validate(request))
            if (request.key != Fixtures.configKey) {
                throw CoSkyException.resourceNotFound(request.key)
            }
            Fixtures.config
        } catch (error: CoSkyException) {
            throw error.toStatusRuntimeException()
        }
    }

    private val leases = object : LeaseServiceGrpcKt.LeaseServiceCoroutineImplBase() {
        override fun keepAlive(requests: Flow<KeepAliveRequest>): Flow<KeepAliveResponse> = requests.map {
            keepAliveResponse {
                lease = it.lease
                ttlSeconds = 30
            }
        }
    }

    @BeforeEach
    fun start() {
        val name = InProcessServerBuilder.generateName()
        server = InProcessServerBuilder.forName(name).directExecutor()
            .addService(resources)
            .addService(leases)
            .build()
            .start()
        channel = InProcessChannelBuilder.forName(name).directExecutor().build()
    }

    @AfterEach
    fun stop() {
        channel.shutdownNow()
        server.shutdownNow()
    }

    @Test
    fun `unary call returns the resource`() = runTest {
        ResourceServiceGrpcKt.ResourceServiceCoroutineStub(channel).get(getRequest { key = Fixtures.configKey })
            .assert().isEqualTo(Fixtures.config)
    }

    @Test
    fun `not found carries its detail to the client`() = runTest {
        val missing = Fixtures.configKey.toBuilder().setName("missing.yaml").build()
        val error = runCatching {
            ResourceServiceGrpcKt.ResourceServiceCoroutineStub(channel).get(getRequest { key = missing })
        }.exceptionOrNull()
        CoSkyException.from(error!!)!!.detail.resourceNotFound.key.assert().isEqualTo(missing)
    }

    @Test
    fun `validation failures reach the client as invalid argument`() = runTest {
        val error = runCatching {
            ResourceServiceGrpcKt.ResourceServiceCoroutineStub(channel).get(GetRequest.getDefaultInstance())
        }.exceptionOrNull()
        val recovered = CoSkyException.from(error!!)!!
        recovered.errorCode.httpStatus.assert().isEqualTo(400)
        recovered.detail.badRequest.violationsList.single().field.assert().isEqualTo("key")
    }

    @Test
    fun `bidirectional stream answers every keep-alive`() = runTest {
        val requests = flowOf(1L, 2L).map {
            keepAliveRequest {
                namespace = Fixtures.NAMESPACE
                lease = it
            }
        }
        LeaseServiceGrpcKt.LeaseServiceCoroutineStub(channel).keepAlive(requests).toList().map { it.lease }
            .assert().containsExactly(1L, 2L)
    }
}
