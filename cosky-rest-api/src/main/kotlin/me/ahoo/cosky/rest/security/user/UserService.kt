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

import io.github.oshai.kotlinlogging.KotlinLogging
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
    private val passwordHasher: PasswordHasher = PasswordHasher()
) {

    fun initRoot(enforce: Boolean): Mono<Boolean> {
        return Mono.from(if (enforce) removeUserInternal(CoSecPrincipal.ROOT_ID) else Mono.empty())
            .then(
                Mono.defer {
                    val coskyPwd = randomPwd(ROOT_PASSWORD_LENGTH)
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
            "---------------- ****** CoSky -  init super user:[${CoSecPrincipal.ROOT_ID}] " +
                "password:[$coskyPwd] ****** ----------------",
        )
    }

    fun query(): Mono<out List<CoSecPrincipal>> {
        return redisTemplate.opsForHash<String, String>().keys(USER_IDX)
            .flatMap { username ->
                Mono.zip(
                    getRoleBind(username).collect(Collectors.toSet()),
                    isLocked(username),
                ).map { userState ->
                    SimplePrincipal(
                        id = username,
                        roles = userState.t1,
                        attributes = mapOf(LOCKED_ATTRIBUTE to userState.t2),
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
        return passwordHasher.hash(pwd).flatMap {
            redisTemplate.opsForHash<String, String>().putIfAbsent(USER_IDX, username, it)
        }
    }

    fun removeUser(username: String): Mono<Boolean> {
        if (username == CoSecPrincipal.ROOT_ID) {
            return Mono.error(IllegalArgumentException("Root user cannot be removed."))
        }
        return removeUserInternal(username)
    }

    private fun removeUserInternal(username: String): Mono<Boolean> {
        return executeUserState(USER_OPERATION_REMOVE, username)
            .map { affected -> affected > 0 }
    }

    fun bindRole(username: String, roleBind: Set<String>): Mono<Void> {
        return redisTemplate.execute(
            UserRedisScripts.SCRIPT_USER_BIND_ROLE,
            listOf(getUserRoleBindKey(username)),
            roleBind.toList(),
        ).then()
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
            .flatMap { storedHash ->
                passwordHasher.matches(oldPwd, storedHash).flatMap { matched ->
                    if (!matched) {
                        return@flatMap SecurityException(
                            "username:[$username] - old password is incorrect!",
                        ).toMono()
                    }
                    passwordHasher.hash(newPwd)
                        .flatMap { replacePasswordHash(username, storedHash, it) }
                        .filter { it }
                        .switchIfEmpty(
                            IllegalStateException("username:[$username] - password was changed concurrently!").toMono(),
                        )
                }
            }
    }

    @Throws(SecurityException::class)
    fun login(username: String, pwd: String): Mono<out CoSecPrincipal> {
        return executeUserState(USER_OPERATION_LOGIN_ATTEMPT, username)
            .flatMap { tryCount: Long ->
                if (tryCount == MANUAL_LOCK_RESULT) {
                    return@flatMap manualLockError(username).toMono()
                }
                if (tryCount > MAX_LOGIN_ERROR_TIMES) {
                    return@flatMap failedLoginLockError(username, tryCount).toMono()
                }
                Mono.just(tryCount)
            }
            .flatMap {
                redisTemplate
                    .opsForHash<String, String>()[USER_IDX, username]
                    .switchIfEmpty(
                        SecurityException(
                            "username:[$username] - password is incorrect.!",
                        ).toMono(),
                    )
                    .flatMap { storedHash ->
                        passwordHasher.matches(pwd, storedHash).flatMap { matched ->
                            if (!matched) {
                                return@flatMap SecurityException(
                                    "username:[$username] - password is incorrect.!",
                                ).toMono()
                            }
                            upgradeLegacyHash(username, pwd, storedHash)
                                .then(executeUserState(USER_OPERATION_LOGIN_SUCCESS, username))
                        }
                    }
            }
            .flatMap { lockResult ->
                if (lockResult == MANUAL_LOCK_RESULT) {
                    return@flatMap manualLockError(username).toMono()
                }
                getRoleBind(username).collect(Collectors.toSet())
            }
            .map { roleBind ->
                SimplePrincipal(
                    id = username,
                    roles = roleBind!!,
                )
            }
    }

    /**
     * Replaces a hash of an older version by a BCrypt hash, now that the password is known to be right.
     *
     * A password too long for BCrypt keeps its legacy hash, rather than locking its user out.
     */
    private fun upgradeLegacyHash(username: String, password: String, storedHash: String): Mono<Void> {
        if (!passwordHasher.needsUpgrade(storedHash)) {
            return Mono.empty()
        }
        if (!passwordHasher.canHash(password)) {
            log.warn {
                "username:[$username] - password is longer than ${PasswordHasher.MAX_PASSWORD_BYTES} bytes, " +
                    "keeping its legacy hash until it is changed."
            }
            return Mono.empty()
        }
        return passwordHasher.hash(password)
            .flatMap { replacePasswordHash(username, storedHash, it) }
            .then()
    }

    /**
     * Compare-and-set, so that a concurrent password change is never overwritten.
     */
    private fun replacePasswordHash(username: String, expectedHash: String, newHash: String): Mono<Boolean> {
        return redisTemplate.execute(
            UserRedisScripts.SCRIPT_USER_PASSWORD_CAS,
            listOf(USER_IDX),
            listOf(username, expectedHash, newHash),
        ).next()
    }

    fun unlock(username: String): Mono<Boolean> {
        val loginLockKey = USER_LOGIN_LOCK.format(username)
        return redisTemplate.delete(loginLockKey)
            .map { affected: Long -> affected > 0 }
    }

    fun lock(username: String): Mono<Boolean> {
        if (username == CoSecPrincipal.ROOT_ID) {
            return Mono.error(IllegalArgumentException("Root user cannot be locked."))
        }
        return executeUserState(USER_OPERATION_LOCK, username).flatMap { locked ->
            if (locked > 0) {
                Mono.just(true)
            } else {
                Mono.error(IllegalArgumentException("User does not exist."))
            }
        }
    }

    internal fun isLocked(username: String): Mono<Boolean> {
        return getLoginLock(username)
            .map { lock ->
                lock == MANUAL_LOCK_MARKER || lock.toLongOrNull()?.let { it > MAX_LOGIN_ERROR_TIMES } == true
            }
            .defaultIfEmpty(false)
    }

    fun ensureUnlocked(username: String): Mono<Void> {
        return getLoginLock(username)
            .flatMap { lock ->
                when {
                    lock == MANUAL_LOCK_MARKER -> Mono.error(manualLockError(username))
                    lock.toLongOrNull()?.let { it > MAX_LOGIN_ERROR_TIMES } == true ->
                        Mono.error(failedLoginLockError(username, lock.toLong()))
                    else -> Mono.empty()
                }
            }.then()
    }

    private fun getLoginLock(username: String): Mono<String> {
        val loginLockKey = USER_LOGIN_LOCK.format(username)
        return redisTemplate.opsForValue()[loginLockKey]
    }

    private fun executeUserState(operation: String, username: String): Mono<Long> {
        return redisTemplate.execute(
            UserRedisScripts.SCRIPT_USER_STATE,
            listOf(USER_IDX, getUserRoleBindKey(username), USER_LOGIN_LOCK.format(username)),
            listOf(
                operation,
                username,
                MANUAL_LOCK_MARKER,
                MAX_LOGIN_ERROR_TIMES.toString(),
                LOGIN_LOCK_EXPIRE.toString(),
            ),
        ).next()
    }

    private fun manualLockError(username: String): SecurityException {
        return SecurityException(
            "User:[$username] is locked by an administrator. Contact an administrator to unlock the account.",
        )
    }

    private fun failedLoginLockError(username: String, tryCount: Long): SecurityException {
        val expansion = Math.max(tryCount / MAX_LOGIN_ERROR_TIMES, 1).toInt().toLong()
        val loginLockExpire = Math.min(LOGIN_LOCK_EXPIRE * expansion, MAX_LOGIN_LOCK_EXPIRE)
        return SecurityException(
            "User:[$username] sign in freezes for [${
                Duration.ofMillis(loginLockExpire).toMinutes()
            }] minutes,Too many:[$tryCount] sign in errors!",
        )
    }

    fun logout() = Unit

    companion object {
        private val log = KotlinLogging.logger {}
        const val USER_IDX = Namespaced.SYSTEM + ":user_idx"
        const val USER_ROLE_BIND = Namespaced.SYSTEM + ":user_role_bind:%s"
        const val USER_LOGIN_LOCK = Namespaced.SYSTEM + ":login_lock:%s"
        const val LOCKED_ATTRIBUTE = "locked"
        internal const val MANUAL_LOCK_MARKER = "manual"
        private const val MANUAL_LOCK_RESULT = -1L
        private const val USER_OPERATION_LOCK = "lock"
        private const val USER_OPERATION_REMOVE = "remove"
        private const val USER_OPERATION_LOGIN_ATTEMPT = "login-attempt"
        private const val USER_OPERATION_LOGIN_SUCCESS = "login-success"
        private fun getUserRoleBindKey(username: String): String {
            return USER_ROLE_BIND.format(username)
        }

        const val MAX_LOGIN_ERROR_TIMES = 10
        private const val ROOT_PASSWORD_LENGTH = 10
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
