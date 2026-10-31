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

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class TokensTest {
    @Test
    fun `a generated token parses back`() {
        val token = Tokens.generate()
        Tokens.parse(token.plaintext).assert().isEqualTo(token)
        token.secret.length.assert().isEqualTo(43)
        Tokens.generate().assert().isNotEqualTo(token)
    }

    @Test
    fun `only the right secret matches the hash`() {
        val token = Tokens.generate()
        val hash = Tokens.sha256Hex(token.secret)
        token.matches(hash).assert().isTrue()
        Tokens.generate().matches(hash).assert().isFalse()
    }

    @Test
    fun `anything else is not a token`() {
        listOf("", "cosky_", "cosky_a", "cosky_a_", "cosky__b", "other_a_b", "cosky_a_b_c", "cosky_a!_b").forEach {
            Tokens.parse(it).assert().isNull()
        }
    }
}
