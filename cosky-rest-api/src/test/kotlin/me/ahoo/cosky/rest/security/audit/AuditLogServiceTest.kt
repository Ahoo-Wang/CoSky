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
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.ReactiveListOperations
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Flux
import tools.jackson.module.kotlin.jacksonObjectMapper

class AuditLogServiceTest {
    @Test
    fun `filtered pagination reads bounded batches`() {
        val objectMapper = jacksonObjectMapper()
        val logs = (0 until 750).map { index ->
            objectMapper.writeValueAsString(
                AuditLog("operator", "127.0.0.1", "resource-$index", "READ", 200, "match", index.toLong()),
            )
        }
        val redisTemplate = mock(ReactiveStringRedisTemplate::class.java)

        @Suppress("UNCHECKED_CAST")
        val listOperations = mock(ReactiveListOperations::class.java) as ReactiveListOperations<String, String>
        `when`(redisTemplate.opsForList()).thenReturn(listOperations)
        `when`(
            listOperations.size(AuditLogService.AUDIT_LOG_KEY)
        ).thenReturn(reactor.core.publisher.Mono.just(logs.size.toLong()))
        `when`(listOperations.range(anyString(), anyLong(), anyLong())).thenAnswer { invocation ->
            val start = (logs.size + invocation.getArgument<Long>(1)).toInt().coerceAtLeast(0)
            val end = (logs.size + invocation.getArgument<Long>(2) + 1).toInt().coerceAtMost(logs.size)
            Flux.fromIterable(logs.subList(start, end))
        }

        val response = AuditLogService(objectMapper, redisTemplate)
            .queryLog(510, 10, AuditLogFilter(query = "match"))
            .block()!!

        response.total.assert().isEqualTo(750)
        response.list.map { it.resource }.assert().containsExactly(
            "resource-510",
            "resource-511",
            "resource-512",
            "resource-513",
            "resource-514",
            "resource-515",
            "resource-516",
            "resource-517",
            "resource-518",
            "resource-519",
        )
        verify(listOperations).range(AuditLogService.AUDIT_LOG_KEY, -750, -251)
        verify(listOperations).range(AuditLogService.AUDIT_LOG_KEY, -250, -1)
    }
}
