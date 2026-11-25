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
import com.google.protobuf.InvalidProtocolBufferException
import io.grpc.StatusRuntimeException
import io.grpc.protobuf.StatusProto
import me.ahoo.cosky.api.InternalCoSkyApi
import me.ahoo.cosky.api.v1.BadRequest
import me.ahoo.cosky.api.v1.ErrorDetail
import me.ahoo.cosky.api.v1.ErrorReason
import me.ahoo.cosky.api.v1.LeaseNotFound
import me.ahoo.cosky.api.v1.NamespaceRef
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.ResourceNotFound
import me.ahoo.cosky.api.v1.RevisionConflict
import me.ahoo.cosky.api.v1.WatchQuotaExceeded
import me.ahoo.cosky.api.v1.WatchResponse
import me.ahoo.cosky.api.validation.Violation
import com.google.rpc.Status as RpcStatus

/**
 * A CoSky error: a reason with its typed [detail], convertible to and from a gRPC status (design §4.4).
 *
 * On the wire the [detail] travels packed in `google.rpc.Status.details`, so any gRPC client can read it.
 */
class CoSkyException(
    val detail: ErrorDetail,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    private constructor(detail: ErrorDetail.Builder, message: String) : this(detail.build(), message)

    init {
        ErrorCodes.of(detail.reason)
    }

    val reason: ErrorReason
        get() = detail.reason

    val errorCode: ErrorCode
        get() = ErrorCodes.of(reason)

    fun toRpcStatus(): RpcStatus = RpcStatus.newBuilder()
        .setCode(errorCode.grpcCode.value())
        .setMessage(message.orEmpty())
        .addDetails(Any.pack(detail))
        .build()

    fun toStatusRuntimeException(): StatusRuntimeException = StatusProto.toStatusRuntimeException(toRpcStatus())

    /**
     * Ends a single watch with this error while its stream stays open (§7.3).
     */
    fun toWatchCanceled(): WatchResponse.Canceled = WatchResponse.Canceled.newBuilder()
        .setMessage(message.orEmpty())
        .setDetail(detail)
        .build()

    companion object {
        fun resourceNotFound(key: ResourceKey): CoSkyException = CoSkyException(
            detail(ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND)
                .setResourceNotFound(ResourceNotFound.newBuilder().setKey(key)),
            "${key.kind} ${key.namespace}/${key.name} not found.",
        )

        fun namespaceNotFound(namespace: String): CoSkyException = CoSkyException(
            detail(ErrorReason.ERROR_REASON_NAMESPACE_NOT_FOUND).setNamespace(namespaceRef(namespace)),
            "Namespace $namespace not found.",
        )

        fun namespaceNotEmpty(namespace: String): CoSkyException = CoSkyException(
            detail(ErrorReason.ERROR_REASON_NAMESPACE_NOT_EMPTY).setNamespace(namespaceRef(namespace)),
            "Namespace $namespace still holds Configs or Instances.",
        )

        /**
         * @param currentRevision 0 when the resource does not exist.
         */
        fun revisionConflict(key: ResourceKey, expectedRevision: Long, currentRevision: Long): CoSkyException =
            CoSkyException(
                detail(ErrorReason.ERROR_REASON_REVISION_CONFLICT).setRevisionConflict(
                    RevisionConflict.newBuilder()
                        .setKey(key)
                        .setExpectedRevision(expectedRevision)
                        .setCurrentRevision(currentRevision),
                ),
                "${key.kind} ${key.namespace}/${key.name}: expected revision $expectedRevision, " +
                    "current revision $currentRevision.",
            )

        fun leaseNotFound(namespace: String, lease: Long): CoSkyException = CoSkyException(
            detail(ErrorReason.ERROR_REASON_LEASE_NOT_FOUND).setLeaseNotFound(
                LeaseNotFound.newBuilder().setNamespace(namespace).setLease(lease),
            ),
            "Lease $lease not found in namespace $namespace.",
        )

        /**
         * The violations travel twice in the gRPC trailers, in `grpc-message` and in `grpc-status-details-bin`, and a
         * grpc-java client refuses more than 8 KiB of them by default: it would see a transport error, not this
         * status. So the error lists the first violations while those two trailers stay within [MAX_TRAILER_BYTES],
         * each field and description cut to [MAX_VIOLATION_TEXT] characters, and its message counts the rest.
         */
        @InternalCoSkyApi
        fun invalidArgument(violations: List<Violation>): CoSkyException {
            var listed = emptyList<Violation>()
            var error = badRequest(listed, violations.size)
            for (violation in violations) {
                val more = listed + violation.shortened()
                val candidate = badRequest(more, violations.size)
                // The first one is always listed: shortened, it fits on its own.
                if (listed.isNotEmpty() && candidate.trailerBytes() > MAX_TRAILER_BYTES) {
                    break
                }
                listed = more
                error = candidate
            }
            return error
        }

        fun unauthenticated(message: String): CoSkyException =
            CoSkyException(detail(ErrorReason.ERROR_REASON_UNAUTHENTICATED).build(), message)

        fun permissionDenied(message: String): CoSkyException =
            CoSkyException(detail(ErrorReason.ERROR_REASON_PERMISSION_DENIED).build(), message)

        fun minRevisionTimeout(namespace: String, minRevision: Long): CoSkyException = CoSkyException(
            detail(ErrorReason.ERROR_REASON_MIN_REVISION_TIMEOUT).setNamespace(namespaceRef(namespace)),
            "Namespace $namespace did not reach revision $minRevision in time.",
        )

        fun storeUnavailable(message: String, cause: Throwable? = null): CoSkyException =
            CoSkyException(detail(ErrorReason.ERROR_REASON_STORE_UNAVAILABLE).build(), message, cause)

        fun revisionRegression(namespace: String): CoSkyException = CoSkyException(
            detail(ErrorReason.ERROR_REASON_REVISION_REGRESSION).setNamespace(namespaceRef(namespace)),
            "Namespace $namespace diverged from the store and is reloading.",
        )

        /**
         * @param limit watches allowed on one stream.
         */
        fun watchQuotaExceeded(limit: Int): CoSkyException = CoSkyException(
            detail(ErrorReason.ERROR_REASON_WATCH_QUOTA_EXCEEDED).setWatchQuotaExceeded(
                WatchQuotaExceeded.newBuilder().setLimit(limit),
            ),
            "At most $limit watches are allowed on one stream.",
        )

        /**
         * Recovers the CoSky error from a gRPC `StatusException` / `StatusRuntimeException` (or anything wrapping
         * one); null when [throwable] carries no [ErrorDetail].
         */
        fun from(throwable: Throwable): CoSkyException? {
            val status = StatusProto.fromThrowable(throwable) ?: return null
            val detail = status.detailsList.firstOrNull { it.`is`(ErrorDetail::class.java) } ?: return null
            return try {
                CoSkyException(detail.unpack(ErrorDetail::class.java), status.message, throwable)
            } catch (_: InvalidProtocolBufferException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        /**
         * Recovers the CoSky error from a canceled watch; null when it carries no known reason.
         */
        fun from(canceled: WatchResponse.Canceled): CoSkyException? = try {
            CoSkyException(canceled.detail, canceled.message)
        } catch (_: IllegalArgumentException) {
            null
        }

        /**
         * Bytes that `grpc-message` and `grpc-status-details-bin` of an INVALID_ARGUMENT stay within, leaving room in
         * grpc-java's default 8 KiB for the other headers and trailers of the response.
         */
        internal const val MAX_TRAILER_BYTES = 6 * 1024

        /** Characters an INVALID_ARGUMENT keeps of one violation's field or description. */
        internal const val MAX_VIOLATION_TEXT = 128

        private const val SHORTENED = "..."

        private fun badRequest(listed: List<Violation>, total: Int): CoSkyException {
            val omitted = total - listed.size
            val message = listed.joinToString(separator = "; ", prefix = "Invalid argument: ") {
                "${it.field}: ${it.description}"
            }
            return CoSkyException(
                detail(ErrorReason.ERROR_REASON_INVALID_ARGUMENT).setBadRequest(
                    BadRequest.newBuilder().addAllViolations(listed.map { it.toProto() }),
                ),
                if (omitted > 0) "$message; and $omitted more." else message,
            )
        }

        private fun Violation.shortened(): Violation =
            copy(field = field.shortened(), description = description.shortened())

        private fun String.shortened(): String {
            if (length <= MAX_VIOLATION_TEXT) {
                return this
            }
            val end = MAX_VIOLATION_TEXT - SHORTENED.length
            // Never half of a surrogate pair.
            return take(if (this[end - 1].isHighSurrogate()) end - 1 else end) + SHORTENED
        }

        /**
         * What `grpc-message` and `grpc-status-details-bin` take on the wire: gRPC percent-encodes the message's
         * UTF-8 bytes but printable ASCII other than `%` and `~`, and sends the status in base64 without padding.
         */
        private fun CoSkyException.trailerBytes(): Int {
            val status = toRpcStatus()
            val message = status.message.encodeToByteArray().sumOf { byte ->
                if (byte < PRINTABLE_FIRST || byte >= PRINTABLE_END || byte == PERCENT) PERCENT_ENCODED else 1
            }
            return message + (status.serializedSize * BASE64_CHARS + BASE64_BYTES - 1) / BASE64_BYTES
        }

        private const val PRINTABLE_FIRST: Byte = 0x20
        private const val PRINTABLE_END: Byte = 0x7E
        private const val PERCENT: Byte = 0x25
        private const val PERCENT_ENCODED = 3
        private const val BASE64_BYTES = 3
        private const val BASE64_CHARS = 4

        private fun detail(reason: ErrorReason): ErrorDetail.Builder = ErrorDetail.newBuilder().setReason(reason)

        private fun namespaceRef(namespace: String): NamespaceRef = NamespaceRef.newBuilder()
            .setNamespace(namespace)
            .build()
    }
}
