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

import com.google.common.base.Charsets
import com.google.common.base.Strings
import com.google.common.hash.Hashing
import me.ahoo.cosec.api.principal.CoSecPrincipal
import me.ahoo.cosec.principal.SimplePrincipal
import me.ahoo.cosky.core.Namespaced
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.time.Duration
import java.util.*
import java.util.stream.Collectors

/**
 * User Service.
 *
 * @author ahoo wang
 */
@Service
class UserService(
    private val redisTemplate: ReactiveStringRedisTemplate,
) {

    fun initRoot(enforce: Boolean): Mono<Boolean> {
        return Mono.from(if (enforce) removeUser(CoSecPrincipal.ROOT_ID) else Mono.empty())
            .then(
                Mono.defer {
                    val coskyPwd = randomPwd(10)
                    addUser(CoSecPrincipal.ROOT_ID, coskyPwd)
                        .map { result: Boolean ->
                            if (result) {
                                printSuperUserPwd(coskyPwd)
                            }
                            result
                        }
                },
            )
    }

    private fun printSuperUserPwd(coskyPwd: String) {
        println(
            "---------------- ****** CoSky -  init super user:[${CoSecPrincipal.ROOT_ID}] password:[$coskyPwd] ****** ----------------",
        )
    }

    fun query(): Mono<out List<CoSecPrincipal>> {
        return redisTemplate.opsForHash<String, String>().keys(USER_IDX)
            .flatMap { username ->
                getRoleBind(username)
                    .collect(Collectors.toSet())
                    .map { roleBind ->
                        SimplePrincipal(
                            id = username!!,
                            roles = roleBind!!,
                        )
                    }
            }
            .collectList()
    }

    fun existsUser(username: String): Mono<Boolean> {
        return redisTemplate
            .opsForHash<Any, Any>()
            .hasKey(USER_IDX, username)
    }

    fun addUser(username: String, pwd: String): Mono<Boolean> {
        return redisTemplate
            .opsForHash<Any, Any>()
            .putIfAbsent(USER_IDX, username, encodePwd(pwd))
    }

    fun removeUser(username: String): Mono<Boolean> {
        val userRoleBindKey = getUserRoleBindKey(username)
        return redisTemplate.delete(userRoleBindKey)
            .then(
                redisTemplate
                    .opsForHash<Any, Any>()
                    .remove(USER_IDX, username),
            ).map { affected -> affected > 0 }
    }

    fun bindRole(username: String, roleBind: Set<String>): Mono<Void> {
        val userRoleBindKey = getUserRoleBindKey(username)
        return redisTemplate
            .opsForSet()
            .delete(userRoleBindKey)
            .thenMany(Flux.fromIterable(roleBind))
            .flatMap { role -> redisTemplate.opsForSet().add(userRoleBindKey, role) }
            .then()
    }

    fun getRoleBind(username: String): Flux<String> {
        val userRoleBindKey = getUserRoleBindKey(username)
        return redisTemplate
            .opsForSet()
            .members(userRoleBindKey)
    }

    fun changePwd(username: String, oldPwd: String, newPwd: String): Mono<Boolean> {
        return redisTemplate
            .opsForHash<String, String>()[USER_IDX, username]
            .switchIfEmpty(
                IllegalArgumentException(
                    "username:[$username] not exists!",
                ).toMono(),
            )
            .flatMap { preEncodePwd: String ->
                val oldEncodePwd = encodePwd(oldPwd)
                val newEncodePwd = encodePwd(newPwd)
                if (preEncodePwd != oldEncodePwd) {
                    return@flatMap SecurityException(
                        "username:[$username] - old password is incorrect!",
                    ).toMono()
                }
                redisTemplate.opsForHash<Any, Any>().put(USER_IDX, username, newEncodePwd)
            }
    }

    @Throws(SecurityException::class)
    fun auth(username: String, pwd: String): Mono<out CoSecPrincipal> {
        val loginLockKey = Strings.lenientFormat(USER_LOGIN_LOCK, username)
        return redisTemplate
            .opsForValue()
            .increment(loginLockKey)
            .flatMap { tryCount: Long ->
                val expansion = Math.max(tryCount / MAX_LOGIN_ERROR_TIMES, 1).toInt().toLong()
                val loginLockExpire = Math.min(LOGIN_LOCK_EXPIRE * expansion, MAX_LOGIN_LOCK_EXPIRE)
                if (tryCount > MAX_LOGIN_ERROR_TIMES) {
                    return@flatMap SecurityException(
                        "User:[$username] sign in freezes for [${
                            Duration.ofMillis(loginLockExpire).toMinutes()
                        }] minutes,Too many:[$tryCount] sign in errors!",
                    ).toMono()
                }
                redisTemplate.expire(loginLockKey, Duration.ofMillis(loginLockExpire))
            }
            .flatMap {
                redisTemplate
                    .opsForHash<String, String>()[USER_IDX, username]
                    .switchIfEmpty(
                        SecurityException(
                            "username:[$username] - password is incorrect.!",
                        ).toMono(),
                    )
                    .flatMap { realEncodedPwd: String ->
                        val encodedPwd = encodePwd(pwd)
                        if (realEncodedPwd != encodedPwd) {
                            return@flatMap SecurityException(
                                "username:[$username] - password is incorrect.!",
                            ).toMono()
                        }
                        redisTemplate.delete(loginLockKey)
                    }
            }
            .flatMap { getRoleBind(username).collect(Collectors.toSet()) }
            .map { roleBind ->
                SimplePrincipal(
                    id = username,
                    roles = roleBind!!,
                )
            }
    }

    private fun encodePwd(pwd: String): String {
        return Hashing.sha256().hashString(pwd, Charsets.UTF_8).toString()
    }

    fun unlock(username: String): Mono<Boolean> {
        val loginLockKey = Strings.lenientFormat(USER_LOGIN_LOCK, username)
        return redisTemplate.delete(loginLockKey)
            .map { affected: Long -> affected > 0 }
    }

    fun logout() = Unit

    companion object {
        const val USER_IDX = Namespaced.SYSTEM + ":user_idx"
        const val USER_ROLE_BIND = Namespaced.SYSTEM + ":user_role_bind:%s"
        const val USER_LOGIN_LOCK = Namespaced.SYSTEM + ":login_lock:%s"
        private fun getUserRoleBindKey(username: String): String {
            return Strings.lenientFormat(USER_ROLE_BIND, username)
        }

        const val MAX_LOGIN_ERROR_TIMES = 10
        val LOGIN_LOCK_EXPIRE = Duration.ofMinutes(15).toMillis()
        val MAX_LOGIN_LOCK_EXPIRE = Duration.ofDays(3).toMillis()
        private const val CANDIDATE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvxyz"

        fun randomPwd(pwdLength: Int): String {
            return buildString(pwdLength) {
                repeat(pwdLength) {
                    append(CANDIDATE_CHARS.random())
                }
            }
        }
    }
}
