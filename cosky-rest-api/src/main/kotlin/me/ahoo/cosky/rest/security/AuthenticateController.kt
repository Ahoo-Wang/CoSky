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

import me.ahoo.cosky.rest.security.annotation.AllowAnonymous
import me.ahoo.cosky.rest.security.user.LoginRequest
import me.ahoo.cosky.rest.security.user.LoginResponse
import me.ahoo.cosky.rest.security.user.RefreshRequest
import me.ahoo.cosky.rest.security.user.UserService
import me.ahoo.cosky.rest.support.RequestPathPrefix
import org.springframework.web.bind.annotation.CrossOrigin
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
@CrossOrigin("*")
@RestController
@RequestMapping(RequestPathPrefix.AUTHENTICATE_PREFIX)
@AllowAnonymous
class AuthenticateController(private val jwtProvider: JwtProvider, private val userService: UserService) {
    @PostMapping("/{username}/login")
    fun login(@PathVariable username: String, @RequestBody loginRequest: LoginRequest): Mono<LoginResponse> {
        return userService.login(username, loginRequest.password)
    }

    @PostMapping("/{username}/refresh")
    fun refresh(@PathVariable username: String, @RequestBody refreshRequest: RefreshRequest): LoginResponse {
        return jwtProvider.refresh(refreshRequest.accessToken, refreshRequest.refreshToken)
    }
}
