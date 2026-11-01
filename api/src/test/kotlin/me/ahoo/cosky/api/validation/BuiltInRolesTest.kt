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

package me.ahoo.cosky.api.validation

import me.ahoo.cosky.api.v1.ViolationCode
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.putRequest
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.v1.roleSpec
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class BuiltInRolesTest {
    private fun violationsOfRole(roleName: String) = Validator.DEFAULT.validate(
        putRequest {
            resource = resource {
                meta = objectMeta {
                    namespace = NamingRules.SYSTEM_NAMESPACE
                    name = roleName
                }
                role = roleSpec {}
            }
        },
    )

    @Test
    fun `a role cannot take the name of a built-in one`() {
        NamingRules.BUILT_IN_ROLES.forEach { name ->
            violationsOfRole(name).map { it.code }.assert().contains(ViolationCode.VIOLATION_CODE_RESERVED_NAME)
        }
    }

    @Test
    fun `any other name is free`() {
        violationsOfRole("reader").map { it.code }.assert().doesNotContain(ViolationCode.VIOLATION_CODE_RESERVED_NAME)
    }
}
