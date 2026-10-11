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

import me.ahoo.cosid.test.MockIdGenerator
import me.ahoo.cosky.test.AbstractReactiveRedisTest
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import reactor.kotlin.test.test
import java.security.MessageDigest
import java.util.HexFormat

class UserPasswordTest : AbstractReactiveRedisTest() {
    private lateinit var userService: UserService
    private val username = "user-" + MockIdGenerator.INSTANCE.generateAsString()

    override fun afterInitializedRedisClient() {
        userService = UserService(redisTemplate)
    }

    override fun beforeDestroyRedisClient() {
        userService.removeUser(username).block()
    }

    private fun storedHash(): String {
        return redisTemplate.opsForHash<String, String>()[UserService.USER_IDX, username].block()!!
    }

    private fun legacyHash(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }

    @Test
    fun newPasswordsAreBCryptHashed() {
        userService.addUser(username, "secret-1").test().expectNext(true).verifyComplete()

        storedHash().startsWith("$2").assert().isTrue()
        userService.login(username, "secret-1").test().expectNextCount(1).verifyComplete()
        userService.login(username, "wrong").test().expectError(SecurityException::class.java).verify()
    }

    @Test
    fun legacyHashIsUpgradedOnSuccessfulLogin() {
        redisTemplate.opsForHash<String, String>().put(UserService.USER_IDX, username, legacyHash("secret-1")).block()

        userService.login(username, "wrong").test().expectError(SecurityException::class.java).verify()
        storedHash().assert().isEqualTo(legacyHash("secret-1"))

        userService.login(username, "secret-1").test().expectNextCount(1).verifyComplete()
        storedHash().startsWith("$2").assert().isTrue()
        userService.login(username, "secret-1").test().expectNextCount(1).verifyComplete()
    }

    @Test
    fun changePasswordFromLegacyHash() {
        redisTemplate.opsForHash<String, String>().put(UserService.USER_IDX, username, legacyHash("old")).block()

        userService.changePwd(username, "wrong", "new").test().expectError(SecurityException::class.java).verify()
        userService.changePwd(username, "old", "new").test().expectNext(true).verifyComplete()

        storedHash().startsWith("$2").assert().isTrue()
        userService.login(username, "new").test().expectNextCount(1).verifyComplete()
    }

    @Test
    fun legacyUserWithPasswordBCryptCanNotHashStillLogsIn() {
        val longPassword = "p".repeat(PasswordHasher.MAX_PASSWORD_BYTES + 1)
        redisTemplate.opsForHash<String, String>().put(UserService.USER_IDX, username, legacyHash(longPassword)).block()

        userService.login(username, longPassword).test().expectNextCount(1).verifyComplete()
        storedHash().assert().isEqualTo(legacyHash(longPassword))
    }

    @Test
    fun rejectPasswordsLongerThanBCryptSupports() {
        val longPassword = "p".repeat(PasswordHasher.MAX_PASSWORD_BYTES + 1)
        userService.addUser(username, longPassword).test().expectErrorSatisfies {
            it.assert().isInstanceOf(IllegalArgumentException::class.java)
            it.message.assert().contains("${PasswordHasher.MAX_PASSWORD_BYTES} bytes")
        }.verify()

        userService.addUser(username, "old").test().expectNext(true).verifyComplete()
        userService.changePwd(username, "old", longPassword).test()
            .expectError(IllegalArgumentException::class.java).verify()
        userService.login(username, "old").test().expectNextCount(1).verifyComplete()
    }
}
