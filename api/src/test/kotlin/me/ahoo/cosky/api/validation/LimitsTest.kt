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

import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.ErrorReason
import me.ahoo.cosky.api.v1.FieldViolation
import me.ahoo.cosky.api.v1.ViolationCode
import me.ahoo.test.asserts.assert
import me.ahoo.test.asserts.assertThrownBy
import org.junit.jupiter.api.Test

class LimitsTest {

    @Test
    fun `defaults follow the design`() {
        val limits = Limits.DEFAULT
        limits.maxConfigContentBytes.assert().isEqualTo(1_048_576)
        limits.maxMetadataEntries.assert().isEqualTo(64)
        limits.maxMetadataKeyLength.assert().isEqualTo(63)
        limits.maxMetadataValueLength.assert().isEqualTo(1024)
        limits.maxLabels.assert().isEqualTo(32)
        limits.maxLabelKeyLength.assert().isEqualTo(63)
        limits.maxLabelValueLength.assert().isEqualTo(253)
        limits.maxTokens.assert().isEqualTo(2)
        limits.maxDescriptionLength.assert().isEqualTo(1024)
        limits.defaultLeaseTtlSeconds.assert().isEqualTo(30)
        limits.minLeaseTtlSeconds.assert().isEqualTo(5)
        limits.maxLeaseTtlSeconds.assert().isEqualTo(300)
        limits.maxRuleNames.assert().isEqualTo(64)
        limits.defaultPageSize.assert().isEqualTo(500)
        limits.maxPageSize.assert().isEqualTo(1000)
        limits.maxPageTokenLength.assert().isEqualTo(1024)
        limits.maxMessageBytes.assert().isEqualTo(2_097_152)
        limits.maxBatchOperations.assert().isEqualTo(100)
        limits.maxBatchBytes.assert().isEqualTo(8_388_608)
        limits.namespaceObjectSoftLimit.assert().isEqualTo(100_000)
        limits.configHistorySize.assert().isEqualTo(20)
    }

    @Test
    fun `minimum TTL must be positive and not above the maximum`() {
        assertThrownBy<IllegalArgumentException> { Limits(minLeaseTtlSeconds = 0) }
        assertThrownBy<IllegalArgumentException> { Limits(minLeaseTtlSeconds = 301) }
    }

    @Test
    fun `default TTL must lie within the bounds`() {
        assertThrownBy<IllegalArgumentException> { Limits(defaultLeaseTtlSeconds = 4) }
        assertThrownBy<IllegalArgumentException> { Limits(defaultLeaseTtlSeconds = 301) }
        Limits(minLeaseTtlSeconds = 30, defaultLeaseTtlSeconds = 30, maxLeaseTtlSeconds = 30)
            .defaultLeaseTtlSeconds.assert().isEqualTo(30)
    }

    @Test
    fun `default page size must lie within the bounds`() {
        assertThrownBy<IllegalArgumentException> { Limits(defaultPageSize = 0) }
        assertThrownBy<IllegalArgumentException> { Limits(defaultPageSize = 1001) }
        Limits(defaultPageSize = 10, maxPageSize = 10).defaultPageSize.assert().isEqualTo(10)
    }

    @Test
    fun `a message must hold the largest config`() {
        assertThrownBy<IllegalArgumentException> { Limits(maxMessageBytes = 1_048_576) }
        Limits(maxConfigContentBytes = 10, maxMessageBytes = 11).maxMessageBytes.assert().isEqualTo(11)
    }

    @Test
    fun `violation converts to and from proto`() {
        val violation = Violation("meta.name", ViolationCode.VIOLATION_CODE_REQUIRED, "Name is required.")
        val proto = violation.toProto()
        proto.assert().isEqualTo(
            FieldViolation.newBuilder()
                .setField("meta.name")
                .setCode(ViolationCode.VIOLATION_CODE_REQUIRED)
                .setDescription("Name is required.")
                .build(),
        )
        Violation.fromProto(proto).assert().isEqualTo(violation)
    }

    @Test
    fun `requireValid passes without violations`() {
        requireValid(emptyList())
    }

    @Test
    fun `requireValid throws invalid argument with the violations`() {
        val violations = Validator.DEFAULT.validate(Fixtures.config.toBuilder().clearMeta().build())
        assertThrownBy<CoSkyException> { requireValid(violations) }
            .matches { it.reason == ErrorReason.ERROR_REASON_INVALID_ARGUMENT }
            .matches { it.detail.badRequest.violationsList.map(Violation::fromProto) == violations }
    }
}
