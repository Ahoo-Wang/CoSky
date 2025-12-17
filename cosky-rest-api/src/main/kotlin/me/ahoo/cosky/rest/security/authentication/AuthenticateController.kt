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

import io.swagger.v3.oas.annotations.tags.Tag
import me.ahoo.cosec.api.token.CompositeToken
import me.ahoo.cosec.token.TokenCompositeAuthentication
import me.ahoo.cosky.rest.security.user.LoginRequest
import me.ahoo.cosky.rest.support.RequestPathPrefix
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * Authenticate Controller.
 *
 * @author ahoo wang
 */
@RestController
@RequestMapping(RequestPathPrefix.AUTHENTICATE_PREFIX)
@Tag(name = "Authenticate")
class AuthenticateController(private val tokenCompositeAuthentication: TokenCompositeAuthentication) {
    @PostMapping("/{username}/login")
    fun login(@PathVariable username: String, @RequestBody loginRequest: LoginRequest): Mono<out CompositeToken> {
        return tokenCompositeAuthentication.authenticateAsToken(
            UserPasswordCredentials(
                username,
                loginRequest.password,
            ),
        )
    }

    @Suppress("UnusedParameter")
    @PostMapping("/{username}/refresh")
    fun refresh(
        @PathVariable username: String,
        @RequestBody refreshRequest: DefaultRefreshTokenCredentials
    ): Mono<out CompositeToken> {
        return tokenCompositeAuthentication.authenticateAsToken(
            refreshRequest,
        )
    }
}
