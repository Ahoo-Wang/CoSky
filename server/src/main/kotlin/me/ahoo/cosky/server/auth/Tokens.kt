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

package me.ahoo.cosky.server.auth

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * A ServiceAccount token, `cosky_<id>_<secret>` (design §7.8). Only the hash of the secret is stored.
 */
data class ParsedToken(val id: String, val secret: String) {
    val plaintext: String
        get() = "$PREFIX$id$SEPARATOR$secret"

    /**
     * Compares in constant time.
     */
    fun matches(sha256Hex: String): Boolean =
        MessageDigest.isEqual(Tokens.sha256Hex(secret).encodeToByteArray(), sha256Hex.encodeToByteArray())

    companion object {
        const val PREFIX = "cosky_"
        const val SEPARATOR = "_"
    }
}

object Tokens {
    private const val ID_LENGTH = 16

    // Holds the 32 random bytes of design 7.8: each character carries almost 6 bits.
    private const val SECRET_LENGTH = 43
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private val BASE62 = Regex("[0-9A-Za-z]+")
    private val random = SecureRandom()

    fun parse(plaintext: String): ParsedToken? {
        val fields = plaintext.removePrefix(ParsedToken.PREFIX).split(ParsedToken.SEPARATOR)
        val wellFormed = plaintext.startsWith(ParsedToken.PREFIX) &&
            fields.size == 2 &&
            fields.all { BASE62.matches(it) }
        return if (wellFormed) ParsedToken(fields[0], fields[1]) else null
    }

    fun generate(): ParsedToken = ParsedToken(base62(ID_LENGTH), base62(SECRET_LENGTH))

    fun sha256Hex(secret: String): String = MessageDigest.getInstance("SHA-256")
        .digest(secret.encodeToByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun base62(length: Int): String = String(CharArray(length) { ALPHABET[random.nextInt(ALPHABET.length)] })
}
