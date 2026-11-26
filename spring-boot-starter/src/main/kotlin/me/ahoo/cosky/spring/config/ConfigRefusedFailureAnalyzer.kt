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

package me.ahoo.cosky.spring.config

import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.ErrorReason
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer
import org.springframework.boot.diagnostics.FailureAnalysis
import org.springframework.core.Ordered

/**
 * Says why the server refused a required Config at start, and what to set: Spring's own analysis would say that it
 * does not exist, and suggest starting without it. Ahead of Spring's, which takes any Config that is not there; one
 * that is simply not there it leaves to Spring.
 */
internal class ConfigRefusedFailureAnalyzer :
    AbstractFailureAnalyzer<ConfigDataResourceNotFoundException>(),
    Ordered {
    override fun analyze(rootFailure: Throwable, cause: ConfigDataResourceNotFoundException): FailureAnalysis? {
        val resource = cause.resource as? CoSkyConfigDataResource ?: return null
        val refusal = cause.cause as? CoSkyException ?: return null
        val config = resource.config
        val namespace = resource.properties.namespace
        val description = "CoSky refused to load Config $config of namespace $namespace: " +
            listOfNotNull(refusal.errorCode.grpcCode.name, refusal.message).joinToString(": ")
        val action = when (refusal.reason) {
            ErrorReason.ERROR_REASON_UNAUTHENTICATED ->
                "Set cosky.token (COSKY_TOKEN) to a ServiceAccount token, one whose account is bound to a role that " +
                    "may read Configs in namespace $namespace, such as the built-in role client."

            ErrorReason.ERROR_REASON_PERMISSION_DENIED ->
                "Bind the token's ServiceAccount to a role that may read Config $config in namespace $namespace, " +
                    "such as the built-in role client."

            ErrorReason.ERROR_REASON_NAMESPACE_NOT_FOUND ->
                "Create namespace $namespace, or set cosky.namespace to the namespace of Config $config."

            else -> "Check that the value '$resource' is correct, or prefix it with 'optional:' to start without it."
        }
        return FailureAnalysis(description, action, cause)
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE
}
