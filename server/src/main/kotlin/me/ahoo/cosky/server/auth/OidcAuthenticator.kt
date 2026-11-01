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

package me.ahoo.cosky.server.auth

import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.Subject
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

/**
 * Verifies the OIDC ID token of a person (design §6.3, §7.8): the server is only a resource server, and the Dashboard
 * does the sign-in. The decoder caches the provider's keys.
 */
class OidcAuthenticator(private val decoder: JwtDecoder, private val groupsClaim: String) {
    fun authenticate(token: String): Principal {
        val jwt = decode(token)
        val subject = jwt.subject ?: throw CoSkyException.unauthenticated("The token names no subject.")
        return Principal(Subject.Type.TYPE_USER, subject, jwt.getClaimAsStringList(groupsClaim).orEmpty().toSet())
    }

    private fun decode(token: String): Jwt = try {
        decoder.decode(token)
    } catch (@Suppress("SwallowedException") e: BadJwtException) {
        throw CoSkyException.unauthenticated("The token is not valid: ${e.message}")
    } catch (e: JwtException) {
        // The provider's keys could not be fetched, which says nothing about the token.
        throw CoSkyException.storeUnavailable("The identity provider cannot be reached.", e)
    }

    companion object {
        fun create(issuer: String, audience: String, groupsClaim: String): OidcAuthenticator {
            val decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build()
            decoder.setJwtValidator(validator(issuer, audience))
            return OidcAuthenticator(decoder, groupsClaim)
        }

        /**
         * `iss`, `exp` and `aud`.
         */
        fun validator(issuer: String, audience: String): OAuth2TokenValidator<Jwt> = DelegatingOAuth2TokenValidator(
            JwtValidators.createDefaultWithIssuer(issuer),
            OAuth2TokenValidator { jwt ->
                if (audience in jwt.audience.orEmpty()) {
                    OAuth2TokenValidatorResult.success()
                } else {
                    OAuth2TokenValidatorResult.failure(
                        OAuth2Error("invalid_token", "The token is for another audience.", null),
                    )
                }
            },
        )
    }
}
