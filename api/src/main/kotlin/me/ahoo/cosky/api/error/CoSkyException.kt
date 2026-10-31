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

        fun invalidArgument(violations: List<Violation>): CoSkyException = CoSkyException(
            detail(ErrorReason.ERROR_REASON_INVALID_ARGUMENT).setBadRequest(
                BadRequest.newBuilder().addAllViolations(violations.map { it.toProto() }),
            ),
            violations.joinToString(separator = "; ", prefix = "Invalid argument: ") {
                "${it.field}: ${it.description}"
            },
        )

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

        private fun detail(reason: ErrorReason): ErrorDetail.Builder = ErrorDetail.newBuilder().setReason(reason)

        private fun namespaceRef(namespace: String): NamespaceRef = NamespaceRef.newBuilder()
            .setNamespace(namespace)
            .build()
    }
}
