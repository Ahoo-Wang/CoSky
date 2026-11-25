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
package me.ahoo.cosky.api.error

import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.stub.StreamObserver
import me.ahoo.cosky.api.v1.PutRequest
import me.ahoo.cosky.api.v1.PutResponse
import me.ahoo.cosky.api.v1.ResourceServiceGrpc
import me.ahoo.cosky.api.v1.ViolationCode
import me.ahoo.cosky.api.v1.putRequest
import me.ahoo.cosky.api.validation.Fixtures
import me.ahoo.cosky.api.validation.Limits
import me.ahoo.cosky.api.validation.Validator
import me.ahoo.cosky.api.validation.Violation
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * An INVALID_ARGUMENT reaches a grpc-java client over HTTP/2 however many violations it has. The client refuses more
 * than 8 KiB of trailers by default (`maxInboundMetadataSize`, left at its default here), and would report a transport
 * error in place of the status and its field details.
 */
class InvalidArgumentTrailersTest {
    private lateinit var server: Server
    private lateinit var channel: ManagedChannel

    private val resources = object : ResourceServiceGrpc.ResourceServiceImplBase() {
        override fun put(request: PutRequest, responseObserver: StreamObserver<PutResponse>) {
            val error = CoSkyException.invalidArgument(Validator.DEFAULT.validate(request))
            responseObserver.onError(error.toStatusRuntimeException())
        }
    }

    @BeforeEach
    fun start() {
        server = NettyServerBuilder.forAddress(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
            .addService(resources)
            .build()
            .start()
        channel = NettyChannelBuilder.forAddress(InetSocketAddress(InetAddress.getLoopbackAddress(), server.port))
            .usePlaintext()
            .build()
    }

    @AfterEach
    fun stop() {
        channel.shutdownNow()
        server.shutdownNow()
    }

    private fun putInstance(metadata: Map<String, String>): CoSkyException {
        val request = putRequest {
            resource = Fixtures.instance.toBuilder()
                .apply { instanceBuilder.putAllMetadata(metadata) }
                .build()
        }
        val error = runCatching { ResourceServiceGrpc.newBlockingStub(channel).put(request) }.exceptionOrNull()
        error.assert().isInstanceOf(StatusRuntimeException::class.java)
        val status = Status.fromThrowable(error!!)
        status.code.assert().describedAs(status.description).isEqualTo(Status.Code.INVALID_ARGUMENT)
        return CoSkyException.from(error)!!
    }

    @Test
    fun `as many invalid metadata keys as an Instance may have arrive as invalid argument`() {
        val keys = List(Limits.DEFAULT.maxMetadataEntries) { "prometheus.io/scrape-$it" }

        val error = putInstance(keys.associateWith { "true" })

        val listed = error.detail.badRequest.violationsList
        listed.map { it.code }.toSet().assert().containsExactly(ViolationCode.VIOLATION_CODE_INVALID_FORMAT)
        listed.size.assert().isBetween(1, keys.size - 1)
        error.message.assert().startsWith("Invalid argument: resource.instance.metadata[\"prometheus.io/scrape-0\"]")
            .endsWith("; and ${keys.size - listed.size} more.")
    }

    @Test
    fun `violations of keys percent-encoded on the wire arrive as invalid argument`() {
        val keys = List(200) { "配置/键-$it" }

        val error = putInstance(keys.associateWith { "值" })

        val listed = error.detail.badRequest.violationsList
        listed.first().code.assert().isEqualTo(ViolationCode.VIOLATION_CODE_TOO_MANY)
        error.message.assert().endsWith("; and ${keys.size + 1 - listed.size} more.")
    }

    @Test
    fun `a key too long to list whole arrives shortened`() {
        val key = "键".repeat(100_000)

        val error = putInstance(mapOf(key to "v"))

        val violation = error.detail.badRequest.violationsList.single()
        violation.code.assert().isEqualTo(ViolationCode.VIOLATION_CODE_TOO_LONG)
        violation.field.assert().hasSize(CoSkyException.MAX_VIOLATION_TEXT)
            .startsWith("resource.instance.metadata[\"键键").endsWith("键...")
        error.message.assert().doesNotContain("more.")
    }

    @Test
    fun `a description is shortened too, never within a surrogate pair`() {
        val description = "x".repeat(CoSkyException.MAX_VIOLATION_TEXT - 4) + "\uD83D\uDE00".repeat(10)

        val error = CoSkyException.invalidArgument(
            listOf(Violation("spec", ViolationCode.VIOLATION_CODE_INVALID_FORMAT, description)),
        )

        val shortened = error.detail.badRequest.violationsList.single().description
        shortened.assert().isEqualTo("x".repeat(CoSkyException.MAX_VIOLATION_TEXT - 4) + "...")
    }
}
