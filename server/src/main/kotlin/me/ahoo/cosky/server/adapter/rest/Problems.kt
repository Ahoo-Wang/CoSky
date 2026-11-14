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

package me.ahoo.cosky.server.adapter.rest

import com.google.gson.JsonObject
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.ErrorReason

/**
 * A failure as RFC 9457 problem details (design §4.4): the problem type and title follow the reason, `status` is the
 * HTTP status, `detail` what happened this time, and `instance` the request's path. Beside them, as extension members,
 * `code` is the gRPC status code and `reason` the reason, with the rest of the error model's detail as gRPC carries it.
 */
internal object Problems {
    /** Each reason has a row of its own in the REST API's table of errors. */
    private const val TYPES = "https://cosky.ahoo.me/guide/rest-api#"
    private const val REASON_PREFIX = "ERROR_REASON_"

    private fun words(reason: ErrorReason): List<String> =
        reason.name.removePrefix(REASON_PREFIX).lowercase().split('_')

    /** `https://cosky.ahoo.me/guide/rest-api#revision-conflict` */
    fun typeOf(reason: ErrorReason): String = TYPES + words(reason).joinToString("-")

    /** `Revision conflict` */
    fun titleOf(reason: ErrorReason): String = words(reason).joinToString(" ").replaceFirstChar { it.uppercase() }

    fun of(error: CoSkyException, instance: String): JsonObject {
        val code = error.errorCode
        return JsonObject().apply {
            addProperty("type", typeOf(code.reason))
            addProperty("title", titleOf(code.reason))
            addProperty("status", code.httpStatus)
            error.message?.let { addProperty("detail", it) }
            addProperty("instance", instance)
            addProperty("code", code.grpcCode.name)
            ProtoJson.tree(error.detail).asJsonObject.entrySet().forEach { (name, value) -> add(name, value) }
        }
    }
}
