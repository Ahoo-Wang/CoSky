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

package me.ahoo.cosky.spring.registry

import io.grpc.Status
import io.grpc.StatusException
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer
import org.springframework.boot.diagnostics.FailureAnalysis

/**
 * The server would not take the application's registration for want of a token, or of a role that allows it, so the
 * application cannot start: which service and namespace, and what the server said.
 */
class RegistrationRefusedException(
    val service: String,
    val namespace: String,
    cause: StatusException,
) : IllegalStateException(
    "CoSky refused to register service $service in namespace $namespace: ${cause.status.code}: " +
        cause.status.description,
    cause,
) {
    val code: Status.Code = cause.status.code

    internal companion object {
        val CODES = setOf(Status.Code.UNAUTHENTICATED, Status.Code.PERMISSION_DENIED)
    }
}

/**
 * Says what to set when the server refuses a registration, instead of a gRPC stack trace.
 */
class RegistrationRefusedFailureAnalyzer : AbstractFailureAnalyzer<RegistrationRefusedException>() {
    override fun analyze(rootFailure: Throwable, cause: RegistrationRefusedException): FailureAnalysis {
        val action = if (cause.code == Status.Code.UNAUTHENTICATED) {
            "Set cosky.token (COSKY_TOKEN) to a ServiceAccount token, one whose account is bound to a role that may " +
                "write Instances in namespace ${cause.namespace}, such as the built-in role client."
        } else {
            "Bind the token's ServiceAccount to a role that may write Instances of service ${cause.service} in " +
                "namespace ${cause.namespace}, such as the built-in role client."
        }
        return FailureAnalysis(cause.message, action, cause)
    }
}
