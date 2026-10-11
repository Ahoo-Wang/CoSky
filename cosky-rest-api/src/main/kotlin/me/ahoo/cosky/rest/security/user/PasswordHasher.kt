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

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat

/**
 * Hashes passwords with BCrypt and still verifies the unsalted SHA-256 hashes of older versions, which
 * [needsUpgrade] reports so that they can be replaced after a successful login.
 *
 * BCrypt is deliberately slow, so hashing runs off the event loop.
 *
 * @author ahoo wang
 */
class PasswordHasher(private val encoder: BCryptPasswordEncoder = BCryptPasswordEncoder()) {
    companion object {
        private val BCRYPT_PREFIX = Regex("^\\$2[aby]?\\$")
        private fun legacyHash(password: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(password.toByteArray(StandardCharsets.UTF_8))
            return HexFormat.of().formatHex(digest)
        }
    }

    fun hash(password: String): Mono<String> {
        return Mono.fromCallable { encoder.encode(password)!! }.subscribeOn(Schedulers.boundedElastic())
    }

    fun matches(password: String, storedHash: String): Mono<Boolean> {
        if (needsUpgrade(storedHash)) {
            val matched = MessageDigest.isEqual(
                legacyHash(password).toByteArray(StandardCharsets.UTF_8),
                storedHash.toByteArray(StandardCharsets.UTF_8),
            )
            return Mono.just(matched)
        }
        return Mono.fromCallable { encoder.matches(password, storedHash) }.subscribeOn(Schedulers.boundedElastic())
    }

    fun needsUpgrade(storedHash: String): Boolean = !BCRYPT_PREFIX.containsMatchIn(storedHash)
}
