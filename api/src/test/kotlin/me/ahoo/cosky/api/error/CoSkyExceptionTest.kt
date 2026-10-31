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

import com.google.protobuf.Any
import com.google.protobuf.ByteString
import com.google.protobuf.StringValue
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.protobuf.StatusProto
import me.ahoo.cosky.api.v1.ErrorDetail
import me.ahoo.cosky.api.v1.ErrorReason
import me.ahoo.cosky.api.v1.ViolationCode
import me.ahoo.cosky.api.validation.Fixtures
import me.ahoo.cosky.api.validation.Violation
import me.ahoo.test.asserts.assert
import me.ahoo.test.asserts.assertThrownBy
import org.junit.jupiter.api.Test
import com.google.rpc.Status as RpcStatus

class CoSkyExceptionTest {

    private val all = listOf(
        CoSkyException.resourceNotFound(Fixtures.configKey),
        CoSkyException.namespaceNotFound("prod"),
        CoSkyException.namespaceNotEmpty("prod"),
        CoSkyException.revisionConflict(Fixtures.configKey, expectedRevision = 3, currentRevision = 5),
        CoSkyException.leaseNotFound("prod", 7),
        CoSkyException.invalidArgument(
            listOf(Violation("meta.name", ViolationCode.VIOLATION_CODE_REQUIRED, "Name is required."))
        ),
        CoSkyException.unauthenticated("No token."),
        CoSkyException.permissionDenied("Not allowed."),
        CoSkyException.minRevisionTimeout("prod", 9),
        CoSkyException.storeUnavailable("Redis down.", IllegalStateException("io")),
        CoSkyException.revisionRegression("prod"),
        CoSkyException.watchQuotaExceeded(1000),
    )

    @Test
    fun `factories cover every reason`() {
        all.map { it.reason }.assert().containsExactlyInAnyOrderElementsOf(ErrorCodes.ALL.map { it.reason })
    }

    @Test
    fun `round trips through a gRPC status`() {
        all.forEach { error ->
            val status = error.toStatusRuntimeException()
            status.status.code.assert().isEqualTo(error.errorCode.grpcCode)
            status.status.description.assert().isEqualTo(error.message)
            val recovered = CoSkyException.from(status)
            recovered!!.detail.assert().isEqualTo(error.detail)
            recovered.message.assert().isEqualTo(error.message)
            recovered.cause.assert().isSameAs(status)
        }
    }

    @Test
    fun `revision conflict carries the current revision`() {
        val detail = CoSkyException.from(
            CoSkyException.revisionConflict(Fixtures.configKey, 3, 5).toStatusRuntimeException(),
        )!!.detail
        detail.revisionConflict.currentRevision.assert().isEqualTo(5)
        detail.revisionConflict.expectedRevision.assert().isEqualTo(3)
        detail.revisionConflict.key.assert().isEqualTo(Fixtures.configKey)
    }

    @Test
    fun `watch quota exceeded carries the limit and can cancel a single watch`() {
        val error = CoSkyException.watchQuotaExceeded(1000)
        error.errorCode.httpStatus.assert().isEqualTo(429)
        val canceled = error.toWatchCanceled()
        canceled.message.assert().isEqualTo(error.message)
        canceled.detail.watchQuotaExceeded.limit.assert().isEqualTo(1000)
    }

    @Test
    fun `invalid argument lists the violations in the message and the detail`() {
        val error = CoSkyException.invalidArgument(
            listOf(
                Violation("meta.name", ViolationCode.VIOLATION_CODE_REQUIRED, "Name is required."),
                Violation("meta.lease", ViolationCode.VIOLATION_CODE_NOT_ALLOWED, "No lease."),
            ),
        )
        error.message.assert().isEqualTo("Invalid argument: meta.name: Name is required.; meta.lease: No lease.")
        error.detail.badRequest.violationsList.map { it.field }.assert().containsExactly("meta.name", "meta.lease")
    }

    @Test
    fun `recovers from a checked status exception`() {
        val rpcStatus = CoSkyException.leaseNotFound("prod", 7).toRpcStatus()
        val recovered = CoSkyException.from(StatusProto.toStatusException(rpcStatus))
        recovered!!.reason.assert().isEqualTo(ErrorReason.ERROR_REASON_LEASE_NOT_FOUND)
        recovered.detail.leaseNotFound.lease.assert().isEqualTo(7)
    }

    @Test
    fun `recovers through a wrapping exception`() {
        val wrapped = IllegalStateException(CoSkyException.namespaceNotFound("prod").toStatusRuntimeException())
        CoSkyException.from(wrapped)!!.reason.assert().isEqualTo(ErrorReason.ERROR_REASON_NAMESPACE_NOT_FOUND)
    }

    @Test
    fun `nothing to recover from other errors`() {
        CoSkyException.from(IllegalStateException("boom")).assert().isNull()
        CoSkyException.from(StatusException(Status.NOT_FOUND)).assert().isNull()
    }

    @Test
    fun `nothing to recover without an error detail`() {
        val status = RpcStatus.newBuilder()
            .setCode(Status.Code.INTERNAL.value())
            .addDetails(Any.pack(StringValue.of("other")))
            .build()
        CoSkyException.from(StatusProto.toStatusRuntimeException(status)).assert().isNull()
    }

    @Test
    fun `nothing to recover from a corrupt or reason-less detail`() {
        val corrupt = Any.newBuilder()
            .setTypeUrl("type.googleapis.com/${ErrorDetail.getDescriptor().fullName}")
            .setValue(ByteString.copyFrom(byteArrayOf(0x08)))
            .build()
        CoSkyException.from(statusWith(corrupt)).assert().isNull()
        CoSkyException.from(statusWith(Any.pack(ErrorDetail.getDefaultInstance()))).assert().isNull()
    }

    @Test
    fun `an error needs a known reason`() {
        assertThrownBy<IllegalArgumentException> { CoSkyException(ErrorDetail.getDefaultInstance(), "no reason") }
    }

    private fun statusWith(detail: Any) = StatusProto.toStatusRuntimeException(
        RpcStatus.newBuilder().setCode(Status.Code.INTERNAL.value()).addDetails(detail).build(),
    )
}
