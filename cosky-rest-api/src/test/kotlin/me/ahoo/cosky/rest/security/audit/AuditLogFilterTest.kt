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
package me.ahoo.cosky.rest.security.audit

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class AuditLogFilterTest {
    private val log = AuditLog(
        operator = "operator",
        ip = "127.0.0.1",
        resource = "/v1/services/payment",
        action = "DELETE",
        status = 503,
        msg = "Service unavailable",
        opTime = 2000,
    )

    @Test
    fun `matches query case insensitively across audit fields`() {
        AuditLogFilter(query = " ").isEmpty.assert().isTrue()
        AuditLogFilter(query = "PAYMENT").matches(log).assert().isTrue()
        AuditLogFilter(query = "unavailable").matches(log).assert().isTrue()
        AuditLogFilter(query = "missing").matches(log).assert().isFalse()
    }

    @Test
    fun `matches time range and status`() {
        AuditLogFilter(from = 1000, to = 3000, successful = false).matches(log).assert().isTrue()
        AuditLogFilter(from = 2001).matches(log).assert().isFalse()
        AuditLogFilter(successful = true).matches(log).assert().isFalse()
    }

    @Test
    fun `escapes csv cells and neutralizes formulas`() {
        AuditLogController.csvCell("message, with \"quotes\"").assert()
            .isEqualTo("\"message, with \"\"quotes\"\"\"")
        AuditLogController.csvCell("=SUM(1,1)").assert().isEqualTo("\"'=SUM(1,1)\"")
    }
}
