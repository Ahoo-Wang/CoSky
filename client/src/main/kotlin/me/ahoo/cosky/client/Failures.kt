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

package me.ahoo.cosky.client

import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException

/**
 * A failure in one line for a log: the gRPC status, and what lies under it, such as a refused connection, which gRPC
 * itself only calls an `io exception`.
 */
internal fun describe(failure: Throwable): String {
    val status: Status? = (failure as? StatusException)?.status ?: (failure as? StatusRuntimeException)?.status
    val said = status?.let { listOfNotNull(it.code.name, it.description).joinToString(": ") } ?: failure.toString()
    val under = (status?.cause ?: failure.cause)?.message
    return if (under == null || under in said) said else "$said ($under)"
}
