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

import jakarta.validation.constraints.NotBlank
import me.ahoo.cosec.api.principal.CoSecPrincipal
import me.ahoo.cosec.api.token.TokenPrincipal
import me.ahoo.cosec.authentication.token.AbstractRefreshTokenAuthentication
import me.ahoo.cosec.authentication.token.RefreshTokenCredentials
import me.ahoo.cosec.token.TokenVerifier
import me.ahoo.cosky.rest.security.user.UserService
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

@Service
class RefreshTokenAuthentication(
    private val tokenVerifier: TokenVerifier,
    private val userService: UserService,
) :
    AbstractRefreshTokenAuthentication<DefaultRefreshTokenCredentials, CoSecPrincipal>(
        DefaultRefreshTokenCredentials::class.java
    ) {
    override val supportCredentials: Class<DefaultRefreshTokenCredentials>
        get() = DefaultRefreshTokenCredentials::class.java

    override fun authenticate(credentials: DefaultRefreshTokenCredentials): Mono<out CoSecPrincipal> {
        return tokenVerifier.refresh<TokenPrincipal>(credentials).toMono()
            .flatMap { principal -> userService.ensureUnlocked(principal.id).thenReturn(principal) }
    }
}

data class DefaultRefreshTokenCredentials(
    @field:NotBlank override val accessToken: String,
    @field:NotBlank override val refreshToken: String
) :
    RefreshTokenCredentials
