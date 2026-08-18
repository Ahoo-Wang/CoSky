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
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
import reactor.core.publisher.Mono
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

    @Test
    fun rootUserCannotBeLocked() {
        val userService = UserService(mock(ReactiveStringRedisTemplate::class.java))

        userService.lock(CoSecPrincipal.ROOT_ID)
            .test()
            .expectErrorMatches { it is IllegalArgumentException && it.message == "Root user cannot be locked." }
            .verify()
    }

    @Test
    fun userLockStateFollowsFailedLoginCount() {
        val redisTemplate = mock(ReactiveStringRedisTemplate::class.java)

        @Suppress("UNCHECKED_CAST")
        val valueOperations = mock(ReactiveValueOperations::class.java) as ReactiveValueOperations<String, String>
        `when`(redisTemplate.opsForValue()).thenReturn(valueOperations)
        `when`(valueOperations[anyString()]).thenReturn(Mono.just("11"))
        val userService = UserService(redisTemplate)

        userService.isLocked("locked-user").test().expectNext(true).verifyComplete()

        `when`(valueOperations[anyString()]).thenReturn(Mono.just("10"))
        userService.isLocked("active-user").test().expectNext(false).verifyComplete()

        `when`(valueOperations[anyString()]).thenReturn(Mono.empty())
        userService.isLocked("new-user").test().expectNext(false).verifyComplete()
    }
}
