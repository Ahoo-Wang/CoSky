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
import me.ahoo.cosky.rest.security.rbac.annotation.AdminResource;
import me.ahoo.cosky.rest.security.rbac.annotation.OwnerResource;
import me.ahoo.cosky.rest.security.user.User;
import me.ahoo.cosky.rest.security.user.UserService;
import me.ahoo.cosky.rest.support.RequestPathPrefix;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

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
    public List<User> query() {
        return userService.query();
    }

    @OwnerResource
    @PatchMapping("/{username}/password")
    public void changePwd(@PathVariable String username, @RequestBody ChangePwdRequest changePwdRequest) {
        userService.changePwd(username, changePwdRequest.getOldPassword(), changePwdRequest.getNewPassword());
    }

    @PostMapping
    public boolean addUser(@RequestBody AddUserRequest addUserRequest) {
        return userService.addUser(addUserRequest.getUsername(), addUserRequest.getPassword());
    }

    @PatchMapping("/{username}/role")
    public void bindRole(@PathVariable String username, @RequestBody Set<String> roleBind) {
        userService.bindRole(username, roleBind);
    }

    @DeleteMapping("/{username}")
    public boolean removeUser(@PathVariable String username) {
        return userService.removeUser(username);
    }

    @DeleteMapping("/{username}/lock")
    public void unlock(@PathVariable String username) {
        userService.unlock(username);
    }
}
