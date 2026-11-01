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

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.ErrorReason
import me.ahoo.cosky.api.v1.Subject
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Date

class OidcAuthenticatorTest {
    private val keys: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val authenticator = OidcAuthenticator(
        NimbusJwtDecoder.withPublicKey(keys.public as RSAPublicKey).build().also {
            it.setJwtValidator(OidcAuthenticator.validator(ISSUER, AUDIENCE))
        },
        "groups",
    )

    private fun token(
        issuer: String = ISSUER,
        audience: String = AUDIENCE,
        expiresAt: Instant = Instant.now().plusSeconds(60),
        key: KeyPair = keys,
    ): String {
        val claims = JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject("ann")
            .expirationTime(Date.from(expiresAt))
            .claim("groups", listOf("devs"))
            .build()
        return SignedJWT(JWSHeader(JWSAlgorithm.RS256), claims)
            .apply { sign(RSASSASigner(key.private)) }
            .serialize()
    }

    private fun rejected(token: String) {
        val failure = runCatching { authenticator.authenticate(token) }.exceptionOrNull() as CoSkyException
        failure.reason.assert().isEqualTo(ErrorReason.ERROR_REASON_UNAUTHENTICATED)
    }

    @Test
    fun `a valid token names the person and their groups`() {
        val principal = authenticator.authenticate(token())
        principal.assert().isEqualTo(Principal(Subject.Type.TYPE_USER, "ann", setOf("devs")))
        principal.id.assert().isEqualTo("user:ann")
    }

    @Test
    fun `a provider out of reach is not a bad token`() {
        val unreachable = OidcAuthenticator({ throw JwtException("Couldn't retrieve the keys.") }, "groups")
        val failure = runCatching { unreachable.authenticate(token()) }.exceptionOrNull() as CoSkyException
        failure.reason.assert().isEqualTo(ErrorReason.ERROR_REASON_STORE_UNAVAILABLE)
    }

    @Test
    fun `an expired token is refused`() {
        rejected(token(expiresAt = Instant.now().minusSeconds(3600)))
    }

    @Test
    fun `another issuer is refused`() {
        rejected(token(issuer = "https://elsewhere"))
    }

    @Test
    fun `another audience is refused`() {
        rejected(token(audience = "someone-else"))
    }

    @Test
    fun `another key is refused`() {
        rejected(token(key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()))
    }

    @Test
    fun `garbage is refused`() {
        rejected("not.a.token")
    }

    private companion object {
        const val ISSUER = "https://idp.example.com"
        const val AUDIENCE = "cosky"
    }
}
