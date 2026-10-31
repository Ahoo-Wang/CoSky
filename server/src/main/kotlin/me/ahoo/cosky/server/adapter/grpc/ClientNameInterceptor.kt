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
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor

/**
 * Picks up the name a client gives itself, the consumer in the topology (design D30). It is only a label and plays no
 * part in authorization, since a client can claim anything; one that is malformed is ignored.
 */
class ClientNameInterceptor : ServerInterceptor {
    override fun <ReqT : Any, RespT : Any> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
        val name = headers[CLIENT_NAME]?.takeIf { WELL_FORMED.matches(it) }
            ?: return next.startCall(call, headers)
        return Contexts.interceptCall(Context.current().withValue(KEY, name), call, headers, next)
    }

    companion object {
        val KEY: Context.Key<String> = Context.key("cosky-client-name")
        private val CLIENT_NAME: Metadata.Key<String> =
            Metadata.Key.of("cosky-client-name", Metadata.ASCII_STRING_MARSHALLER)
        private val WELL_FORMED = Regex("[A-Za-z0-9._-]{1,63}")
    }
}
