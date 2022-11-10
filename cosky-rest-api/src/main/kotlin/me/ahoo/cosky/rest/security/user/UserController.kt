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
package me.ahoo.cosky.rest.security.user

import me.ahoo.cosky.rest.security.annotation.AdminResource
import me.ahoo.cosky.rest.security.annotation.OwnerResource
import me.ahoo.cosky.rest.support.RequestPathPrefix
import me.ahoo.cosky.rest.support.RequestPathPrefix.USERS_USER
import me.ahoo.cosky.rest.support.RequestPathPrefix.USERS_USER_PASSWORD
import me.ahoo.cosky.rest.support.RequestPathPrefix.USERS_USER_ROLE
import me.ahoo.cosky.rest.support.RequestPathPrefix.USERS_USER_UNLOCK
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * User Controller.
 *
 * @author ahoo wang
 */
@CrossOrigin("*")
@RestController
@RequestMapping(RequestPathPrefix.USERS_PREFIX)
@AdminResource
class UserController(private val userService: UserService) {
    @GetMapping
    fun query(): Mono<List<User>> {
        return userService.query()
    }

    @OwnerResource
    @PatchMapping(USERS_USER_PASSWORD)
    fun changePwd(@PathVariable username: String, @RequestBody changePwdRequest: ChangePwdRequest): Mono<Boolean> {
        return userService.changePwd(username, changePwdRequest.oldPassword, changePwdRequest.newPassword)
    }

    @PostMapping(USERS_USER)
    fun addUser(@PathVariable username: String, @RequestBody addUserRequest: AddUserRequest): Mono<Boolean> {
        return userService.addUser(username, addUserRequest.password)
    }

    @PatchMapping(USERS_USER_ROLE)
    fun bindRole(@PathVariable username: String, @RequestBody roleBind: Set<String>): Mono<Void> {
        return userService.bindRole(username, roleBind)
    }

    @DeleteMapping(USERS_USER)
    fun removeUser(@PathVariable username: String): Mono<Boolean> {
        return userService.removeUser(username)
    }

    @DeleteMapping(USERS_USER_UNLOCK)
    fun unlock(@PathVariable username: String): Mono<Boolean> {
        return userService.unlock(username)
    }
}
