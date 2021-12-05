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

import me.ahoo.cosky.rest.dto.user.AddUserRequest;
import me.ahoo.cosky.rest.dto.user.ChangePwdRequest;
import me.ahoo.cosky.rest.security.annotation.AdminResource;
import me.ahoo.cosky.rest.security.annotation.OwnerResource;
import me.ahoo.cosky.rest.security.user.User;
import me.ahoo.cosky.rest.security.user.UserService;
import me.ahoo.cosky.rest.support.RequestPathPrefix;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

import static me.ahoo.cosky.rest.support.RequestPathPrefix.*;

/**
 * @author ahoo wang
 */
@CrossOrigin("*")
@RestController
@RequestMapping(RequestPathPrefix.USERS_PREFIX)
@AdminResource
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Mono<List<User>> query() {
        return userService.query();
    }

    @OwnerResource
    @PatchMapping(USERS_USER_PASSWORD)
    public Mono<Boolean> changePwd(@PathVariable String username, @RequestBody ChangePwdRequest changePwdRequest) {
        return userService.changePwd(username, changePwdRequest.getOldPassword(), changePwdRequest.getNewPassword());
    }

    @PostMapping(USERS_USER)
    public Mono<Boolean> addUser(@PathVariable String username, @RequestBody AddUserRequest addUserRequest) {
        return userService.addUser(username, addUserRequest.getPassword());
    }

    @PatchMapping(USERS_USER_ROLE)
    public Mono<Void> bindRole(@PathVariable String username, @RequestBody Set<String> roleBind) {
        return userService.bindRole(username, roleBind);
    }

    @DeleteMapping(USERS_USER)
    public Mono<Boolean> removeUser(@PathVariable String username) {
        return userService.removeUser(username);
    }

    @DeleteMapping(USERS_USER_UNLOCK)
    public Mono<Boolean> unlock(@PathVariable String username) {
        return userService.unlock(username);
    }
}
