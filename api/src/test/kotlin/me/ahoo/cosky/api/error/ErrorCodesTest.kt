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
    fun `HTTP status follows the reason, not only the gRPC code`() {
        ErrorCodes.of(ErrorReason.ERROR_REASON_NAMESPACE_NOT_EMPTY).assert().isEqualTo(
            ErrorCode(ErrorReason.ERROR_REASON_NAMESPACE_NOT_EMPTY, Status.Code.FAILED_PRECONDITION, 409),
        )
        ErrorCodes.of(ErrorReason.ERROR_REASON_WATCH_QUOTA_EXCEEDED).assert().isEqualTo(
            ErrorCode(ErrorReason.ERROR_REASON_WATCH_QUOTA_EXCEEDED, Status.Code.RESOURCE_EXHAUSTED, 429),
        )
        ErrorCodes.httpStatusOf(Status.Code.FAILED_PRECONDITION).assert().isEqualTo(412)
    }

    @Test
    fun `other reasons agree with the generic gRPC to HTTP mapping`() {
        (ErrorCodes.ALL.filter { it.reason != ErrorReason.ERROR_REASON_NAMESPACE_NOT_EMPTY }).forEach {
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
