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
package me.ahoo.cosky.rest.security

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent
import org.springframework.context.ApplicationListener
import org.springframework.core.env.Environment

/**
 * Refuses to start with security enabled but no JWT secret, instead of signing tokens with a publicly known one.
 *
 * Runs once the environment is prepared, before any bean is created, so that the failure names the setting to fix.
 *
 * @author ahoo wang
 */
class JwtSecretValidator : ApplicationListener<ApplicationEnvironmentPreparedEvent> {
    companion object {
        private val log = KotlinLogging.logger {}
        const val JWT_SECRET_KEY = "cosec.jwt.secret"
        const val RECOMMENDED_MIN_LENGTH = 32
    }

    override fun onApplicationEvent(event: ApplicationEnvironmentPreparedEvent) {
        validate(event.environment)
    }

    fun validate(environment: Environment) {
        val securityEnabled = environment.getProperty(
            ConditionalOnSecurityEnabled.ENABLED_KEY,
            Boolean::class.java,
            true
        )
        if (!securityEnabled) {
            return
        }
        val secret = environment.getProperty(JWT_SECRET_KEY).orEmpty()
        check(secret.isNotBlank()) {
            "The JWT secret is not configured: set `cosky.security.key` (environment variable COSKY_SECURITY_KEY) " +
                "to a random secret of at least $RECOMMENDED_MIN_LENGTH characters, " +
                "or disable security with `${ConditionalOnSecurityEnabled.ENABLED_KEY}=false`."
        }
        if (secret.length < RECOMMENDED_MIN_LENGTH) {
            log.warn {
                "The JWT secret is shorter than $RECOMMENDED_MIN_LENGTH characters, which weakens HMAC-SHA256 token signatures."
            }
        }
    }
}
