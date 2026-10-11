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
package me.ahoo.cosky.rest.security

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.env.MockEnvironment

class JwtSecretValidatorTest {
    private val validator = JwtSecretValidator()

    @Test
    fun rejectMissingSecretWhenSecurityIsEnabled() {
        val error = assertThrows<IllegalStateException> { validator.validate(MockEnvironment()) }
        check(error.message!!.contains("cosky.security.key"))
    }

    @Test
    fun rejectBlankSecret() {
        assertThrows<IllegalStateException> {
            validator.validate(MockEnvironment().withProperty(JwtSecretValidator.JWT_SECRET_KEY, " "))
        }
    }

    @Test
    fun acceptConfiguredSecret() {
        assertDoesNotThrow {
            validator.validate(MockEnvironment().withProperty(JwtSecretValidator.JWT_SECRET_KEY, "x".repeat(32)))
        }
    }

    @Test
    fun skipWhenSecurityIsDisabled() {
        assertDoesNotThrow {
            validator.validate(MockEnvironment().withProperty(ConditionalOnSecurityEnabled.ENABLED_KEY, "false"))
        }
    }
}
