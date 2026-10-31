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

import io.grpc.Context
import io.grpc.Contexts
import io.grpc.Grpc
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.server.audit.AuditLogger
import me.ahoo.cosky.server.auth.AccessControl
import me.ahoo.cosky.server.auth.Caller

/**
 * The caller of the current call, which [AuthInterceptor] put there; anonymous where authentication is off.
 */
internal object AuthContext {
    val CALLER: Context.Key<Caller> = Context.key("cosky-caller")

    fun caller(): Caller = CALLER.get() ?: Caller.ANONYMOUS
}

/**
 * Authenticates every CoSky call from its bearer token (design §6.3), before any of it runs. Others, like health
 * checks, pass through.
 */
class AuthInterceptor(
    private val access: AccessControl,
    private val audit: AuditLogger,
) : ServerInterceptor {
    override fun <ReqT : Any, RespT : Any> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
        if (call.methodDescriptor.serviceName?.startsWith(COSKY_SERVICES) != true) {
            return next.startCall(call, headers)
        }
        val remote = call.attributes[Grpc.TRANSPORT_ATTR_REMOTE_ADDR]?.toString()
        val principal = try {
            access.authenticate(headers[AUTHORIZATION]?.removePrefix(BEARER))
        } catch (e: CoSkyException) {
            val failure = e.toStatusRuntimeException()
            audit.record(
                UNKNOWN_SUBJECT,
                null,
                null,
                null,
                call.methodDescriptor.fullMethodName,
                null,
                failure.status.code.name,
                remote
            )
            call.close(failure.status, failure.trailers ?: Metadata())
            return object : ServerCall.Listener<ReqT>() {}
        }
        val context = Context.current().withValue(AuthContext.CALLER, Caller(principal, remote))
        return Contexts.interceptCall(context, call, headers, next)
    }

    private companion object {
        const val COSKY_SERVICES = "cosky."
        const val BEARER = "Bearer "
        const val UNKNOWN_SUBJECT = "unknown"
        val AUTHORIZATION: Metadata.Key<String> = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
    }
}
