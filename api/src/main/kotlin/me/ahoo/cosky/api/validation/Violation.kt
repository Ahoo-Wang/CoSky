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

import me.ahoo.cosky.api.v1.FieldViolation
import me.ahoo.cosky.api.v1.ViolationCode

/**
 * One failed rule.
 *
 * @param field path in proto field names, e.g. `resource.meta.labels["team"]` or `resource.role.rules[0].kinds`.
 */
data class Violation(
    val field: String,
    val code: ViolationCode,
    val description: String,
) {
    fun toProto(): FieldViolation = FieldViolation.newBuilder()
        .setField(field)
        .setCode(code)
        .setDescription(description)
        .build()

    companion object {
        fun fromProto(violation: FieldViolation): Violation =
            Violation(violation.field, violation.code, violation.description)
    }
}
