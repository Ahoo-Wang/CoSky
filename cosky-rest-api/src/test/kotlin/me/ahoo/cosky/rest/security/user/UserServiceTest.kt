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

import me.ahoo.cosec.api.principal.CoSecPrincipal
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.kotlin.test.test

class UserServiceTest {
    @Test
    fun rootUserCannotBeRemoved() {
        val userService = UserService(mock(ReactiveStringRedisTemplate::class.java))

        userService.removeUser(CoSecPrincipal.ROOT_ID)
            .test()
            .expectErrorMatches { it is IllegalArgumentException && it.message == "Root user cannot be removed." }
            .verify()
    }
}
