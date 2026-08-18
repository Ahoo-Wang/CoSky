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
package me.ahoo.cosky.rest.security.authentication

import me.ahoo.cosec.api.token.TokenPrincipal
import me.ahoo.cosec.token.TokenVerifier
import me.ahoo.cosky.rest.security.user.UserService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import reactor.core.publisher.Mono
import reactor.kotlin.test.test

class RefreshTokenAuthenticationTest {
    @Test
    fun lockedUserCannotRefreshTokens() {
        val credentials = DefaultRefreshTokenCredentials("access-token", "refresh-token")
        val principal = mock(TokenPrincipal::class.java)
        val tokenVerifier = mock(TokenVerifier::class.java)
        val userService = mock(UserService::class.java)
        `when`(principal.id).thenReturn("locked-user")
        `when`(tokenVerifier.refresh<TokenPrincipal>(credentials)).thenReturn(principal)
        `when`(userService.ensureUnlocked("locked-user"))
            .thenReturn(Mono.error(SecurityException("locked")))

        RefreshTokenAuthentication(tokenVerifier, userService)
            .authenticate(credentials)
            .test()
            .expectErrorMatches { it is SecurityException && it.message == "locked" }
            .verify()
    }
}
