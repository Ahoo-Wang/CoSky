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

import me.ahoo.cosky.rest.security.SecurityProperties
import me.ahoo.cosky.test.AbstractReactiveRedisTest
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.kotlin.test.test
import tools.jackson.module.kotlin.jacksonObjectMapper

class AuditLogRetentionTest : AbstractReactiveRedisTest() {

    @Test
    fun keepsNewestMaxSizeEntries() {
        redisTemplate.delete(AuditLogService.AUDIT_LOG_KEY).block()
        val properties = SecurityProperties(auditLog = SecurityProperties.AuditLog(maxSize = 3))
        val auditLogService = AuditLogService(jacksonObjectMapper(), redisTemplate, properties)

        Flux.range(1, 5)
            .concatMap {
                auditLogService.addLog(
                    AuditLog("op", "127.0.0.1", "resource-$it", "WRITE", 200, "", it.toLong())
                )
            }
            .last()
            .test()
            .expectNext(3L)
            .verifyComplete()

        auditLogService.queryLog(0, 10, AuditLogFilter())
            .test()
            .expectNextMatches {
                it.total.assert().isEqualTo(3)
                it.list.map { log -> log.resource }.assert().containsExactly("resource-5", "resource-4", "resource-3")
                true
            }
            .verifyComplete()
        redisTemplate.delete(AuditLogService.AUDIT_LOG_KEY).block()
    }
}
