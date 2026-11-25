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

import io.grpc.Status
import me.ahoo.cosky.api.v1.ErrorReason
import me.ahoo.test.asserts.assert
import me.ahoo.test.asserts.assertThrownBy
import org.junit.jupiter.api.Test

class ErrorCodesTest {

    @Test
    fun `every reason has exactly one error code`() {
        val reasons = ErrorReason.entries - ErrorReason.ERROR_REASON_UNSPECIFIED - ErrorReason.UNRECOGNIZED
        ErrorCodes.ALL.map { it.reason }.assert().containsExactlyInAnyOrderElementsOf(reasons)
    }

    @Test
    fun `codes follow the design table`() {
        ErrorCodes.of(ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND).assert()
            .isEqualTo(ErrorCode(ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND, Status.Code.NOT_FOUND, 404))
        ErrorCodes.of(ErrorReason.ERROR_REASON_REVISION_CONFLICT).assert()
            .isEqualTo(ErrorCode(ErrorReason.ERROR_REASON_REVISION_CONFLICT, Status.Code.ABORTED, 409))
        ErrorCodes.of(ErrorReason.ERROR_REASON_LEASE_NOT_FOUND).assert()
            .isEqualTo(ErrorCode(ErrorReason.ERROR_REASON_LEASE_NOT_FOUND, Status.Code.FAILED_PRECONDITION, 412))
        ErrorCodes.of(ErrorReason.ERROR_REASON_UNAUTHENTICATED).httpStatus.assert().isEqualTo(401)
        ErrorCodes.of(ErrorReason.ERROR_REASON_PERMISSION_DENIED).httpStatus.assert().isEqualTo(403)
        ErrorCodes.of(ErrorReason.ERROR_REASON_INVALID_ARGUMENT).httpStatus.assert().isEqualTo(400)
        ErrorCodes.of(
            ErrorReason.ERROR_REASON_MIN_REVISION_TIMEOUT
        ).grpcCode.assert().isEqualTo(Status.Code.DEADLINE_EXCEEDED)
        ErrorCodes.of(ErrorReason.ERROR_REASON_MIN_REVISION_TIMEOUT).httpStatus.assert().isEqualTo(504)
        ErrorCodes.of(ErrorReason.ERROR_REASON_STORE_UNAVAILABLE).httpStatus.assert().isEqualTo(503)
        ErrorCodes.of(ErrorReason.ERROR_REASON_REVISION_REGRESSION).grpcCode.assert().isEqualTo(Status.Code.UNAVAILABLE)
    }

    @Test
    fun `an identity provider or a replica out of service is unavailable, as a store is`() {
        listOf(ErrorReason.ERROR_REASON_IDENTITY_PROVIDER_UNAVAILABLE, ErrorReason.ERROR_REASON_REPLICA_NOT_READY)
            .forEach { reason ->
                ErrorCodes.of(reason).assert().isEqualTo(ErrorCode(reason, Status.Code.UNAVAILABLE, 503))
            }
    }

    @Test
    fun `HTTP status follows the reason, not only the gRPC code`() {
        ErrorCodes.of(ErrorReason.ERROR_REASON_NAMESPACE_NOT_EMPTY).assert().isEqualTo(
            ErrorCode(ErrorReason.ERROR_REASON_NAMESPACE_NOT_EMPTY, Status.Code.FAILED_PRECONDITION, 409),
        )
        ErrorCodes.of(ErrorReason.ERROR_REASON_WATCH_QUOTA_EXCEEDED).assert().isEqualTo(
            ErrorCode(ErrorReason.ERROR_REASON_WATCH_QUOTA_EXCEEDED, Status.Code.RESOURCE_EXHAUSTED, 429),
        )
        ErrorCodes.of(ErrorReason.ERROR_REASON_LEASE_NOT_FOUND).httpStatus.assert().isEqualTo(412)
    }

    @Test
    fun `a gRPC code without a reason answers as google_rpc_Code maps it`() {
        // The "HTTP Mapping" of each code in google/rpc/code.proto.
        val standard = mapOf(
            Status.Code.OK to 200,
            Status.Code.CANCELLED to 499,
            Status.Code.UNKNOWN to 500,
            Status.Code.INVALID_ARGUMENT to 400,
            Status.Code.DEADLINE_EXCEEDED to 504,
            Status.Code.NOT_FOUND to 404,
            Status.Code.ALREADY_EXISTS to 409,
            Status.Code.PERMISSION_DENIED to 403,
            Status.Code.UNAUTHENTICATED to 401,
            Status.Code.RESOURCE_EXHAUSTED to 429,
            Status.Code.FAILED_PRECONDITION to 400,
            Status.Code.ABORTED to 409,
            Status.Code.OUT_OF_RANGE to 400,
            Status.Code.UNIMPLEMENTED to 501,
            Status.Code.INTERNAL to 500,
            Status.Code.UNAVAILABLE to 503,
            Status.Code.DATA_LOSS to 500,
        )
        standard.forEach { (code, status) -> ErrorCodes.httpStatusOf(code).assert().isEqualTo(status) }
    }

    @Test
    fun `other reasons agree with the generic gRPC to HTTP mapping`() {
        val ownStatus = setOf(ErrorReason.ERROR_REASON_NAMESPACE_NOT_EMPTY, ErrorReason.ERROR_REASON_LEASE_NOT_FOUND)
        ErrorCodes.ALL.filter { it.reason !in ownStatus }.forEach {
            ErrorCodes.httpStatusOf(it.grpcCode).assert().isEqualTo(it.httpStatus)
        }
    }

    @Test
    fun `every gRPC code has an HTTP status`() {
        ErrorCodes.HTTP_STATUS_BY_GRPC_CODE.keys.assert().containsExactlyInAnyOrder(*Status.Code.entries.toTypedArray())
        ErrorCodes.httpStatusOf(Status.Code.UNIMPLEMENTED).assert().isEqualTo(501)
    }

    @Test
    fun `unspecified and unknown reasons have no code`() {
        assertThrownBy<IllegalArgumentException> { ErrorCodes.of(ErrorReason.ERROR_REASON_UNSPECIFIED) }
        assertThrownBy<IllegalArgumentException> { ErrorCodes.of(ErrorReason.UNRECOGNIZED) }
    }
}
