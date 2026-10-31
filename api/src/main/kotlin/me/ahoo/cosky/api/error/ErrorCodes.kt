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

/**
 * How an [ErrorReason] surfaces on each protocol.
 */
data class ErrorCode(
    val reason: ErrorReason,
    val grpcCode: Status.Code,
    val httpStatus: Int,
)

/**
 * The error model of design §4.4 as data: every [ErrorReason] with its gRPC code and HTTP status, plus the HTTP
 * status of gRPC codes that carry no CoSky reason. The HTTP status follows the reason, not the gRPC code:
 * `NAMESPACE_NOT_EMPTY` and `LEASE_NOT_FOUND` share FAILED_PRECONDITION but answer 409 and 412.
 */
object ErrorCodes {
    val ALL: List<ErrorCode> = listOf(
        ErrorCode(ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND, Status.Code.NOT_FOUND, 404),
        ErrorCode(ErrorReason.ERROR_REASON_NAMESPACE_NOT_FOUND, Status.Code.NOT_FOUND, 404),
        ErrorCode(ErrorReason.ERROR_REASON_REVISION_CONFLICT, Status.Code.ABORTED, 409),
        ErrorCode(ErrorReason.ERROR_REASON_LEASE_NOT_FOUND, Status.Code.FAILED_PRECONDITION, 412),
        ErrorCode(ErrorReason.ERROR_REASON_NAMESPACE_NOT_EMPTY, Status.Code.FAILED_PRECONDITION, 409),
        ErrorCode(ErrorReason.ERROR_REASON_UNAUTHENTICATED, Status.Code.UNAUTHENTICATED, 401),
        ErrorCode(ErrorReason.ERROR_REASON_PERMISSION_DENIED, Status.Code.PERMISSION_DENIED, 403),
        ErrorCode(ErrorReason.ERROR_REASON_INVALID_ARGUMENT, Status.Code.INVALID_ARGUMENT, 400),
        ErrorCode(ErrorReason.ERROR_REASON_MIN_REVISION_TIMEOUT, Status.Code.DEADLINE_EXCEEDED, 504),
        ErrorCode(ErrorReason.ERROR_REASON_STORE_UNAVAILABLE, Status.Code.UNAVAILABLE, 503),
        ErrorCode(ErrorReason.ERROR_REASON_REVISION_REGRESSION, Status.Code.UNAVAILABLE, 503),
        ErrorCode(ErrorReason.ERROR_REASON_WATCH_QUOTA_EXCEEDED, Status.Code.RESOURCE_EXHAUSTED, 429),
    )

    private val byReason: Map<ErrorReason, ErrorCode> = ALL.associateBy { it.reason }

    /**
     * HTTP status for gRPC codes without a CoSky reason, following the mapping of `google.rpc.Code`.
     */
    val HTTP_STATUS_BY_GRPC_CODE: Map<Status.Code, Int> = mapOf(
        Status.Code.OK to 200,
        Status.Code.CANCELLED to 499,
        Status.Code.UNKNOWN to 500,
        Status.Code.INVALID_ARGUMENT to 400,
        Status.Code.DEADLINE_EXCEEDED to 504,
        Status.Code.NOT_FOUND to 404,
        Status.Code.ALREADY_EXISTS to 409,
        Status.Code.PERMISSION_DENIED to 403,
        Status.Code.RESOURCE_EXHAUSTED to 429,
        Status.Code.FAILED_PRECONDITION to 412,
        Status.Code.ABORTED to 409,
        Status.Code.OUT_OF_RANGE to 400,
        Status.Code.UNIMPLEMENTED to 501,
        Status.Code.INTERNAL to 500,
        Status.Code.UNAVAILABLE to 503,
        Status.Code.DATA_LOSS to 500,
        Status.Code.UNAUTHENTICATED to 401,
    )

    /**
     * @throws IllegalArgumentException for [ErrorReason.ERROR_REASON_UNSPECIFIED] and unknown reasons.
     */
    fun of(reason: ErrorReason): ErrorCode =
        requireNotNull(byReason[reason]) { "No error code for reason $reason." }

    fun httpStatusOf(code: Status.Code): Int = HTTP_STATUS_BY_GRPC_CODE.getValue(code)
}
