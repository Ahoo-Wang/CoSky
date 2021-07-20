/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

package me.ahoo.cosky.rest.controller;

import me.ahoo.cosky.rest.dto.user.LoginRequest;
import me.ahoo.cosky.rest.dto.user.LoginResponse;
import me.ahoo.cosky.rest.dto.user.RefreshRequest;
import me.ahoo.cosky.rest.security.JwtProvider;
import me.ahoo.cosky.rest.security.user.UserService;
import me.ahoo.cosky.rest.support.RequestPathPrefix;
import org.springframework.web.bind.annotation.*;

/**
 * @author ahoo wang
 */
@CrossOrigin("*")
@RestController
@RequestMapping(RequestPathPrefix.AUTHENTICATE_PREFIX)
public class AuthenticateController {

    private final JwtProvider jwtProvider;
    private final UserService userService;

    public AuthenticateController(JwtProvider jwtProvider, UserService userService) {
        this.jwtProvider = jwtProvider;
        this.userService = userService;
    }

    @PostMapping("/{username}/login")
    public LoginResponse login(@PathVariable String username, @RequestBody LoginRequest loginRequest) {
        return userService.login(username, loginRequest.getPassword());
    }

    @PostMapping("/{username}/refresh")
    public LoginResponse refresh(@PathVariable String username, @RequestBody RefreshRequest refreshRequest) {
        return jwtProvider.refresh(refreshRequest.getAccessToken(), refreshRequest.getRefreshToken());
    }
}
