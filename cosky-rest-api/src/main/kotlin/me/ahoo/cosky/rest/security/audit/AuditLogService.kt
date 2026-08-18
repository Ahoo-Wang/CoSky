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

import me.ahoo.cosky.core.Namespaced
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

/**
 * Audit Log Service.
 *
 * @author ahoo wang
 */
@Service
class AuditLogService(private val objectMapper: ObjectMapper, private val redisTemplate: ReactiveStringRedisTemplate) {
    fun addLog(log: AuditLog): Mono<Long> {
        val logStr = objectMapper.writeValueAsString(log)
        return redisTemplate.opsForList()
            .leftPush(AUDIT_LOG_KEY, logStr)
    }

    fun queryLog(offset: Long, limit: Long, filter: AuditLogFilter): Mono<QueryLogResponse> {
        require(offset >= 0) { "offset must not be negative." }
        require(limit > 0) { "limit must be positive." }
        if (filter.isEmpty) {
            return Mono.zip(
                redisTemplate.opsForList().size(AUDIT_LOG_KEY),
                redisTemplate.opsForList()
                    .range(AUDIT_LOG_KEY, offset, offset + limit - 1)
                    .map { objectMapper.readValue(it, AuditLog::class.java) }
                    .collectList(),
            ).map { QueryLogResponse(it.t2, it.t1) }
        }
        return filteredLogs(filter)
            // ponytail: audit logs currently use one Redis list; add indexed storage when a full scan becomes measurable.
            .collectList()
            .map { logs ->
                QueryLogResponse(
                    logs.drop(
                        offset.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    ).take(
                        limit.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    ),
                    logs.size.toLong(),
                )
            }
    }

    fun queryAll(filter: AuditLogFilter): Flux<AuditLog> = filteredLogs(filter)

    private fun filteredLogs(filter: AuditLogFilter): Flux<AuditLog> {
        return redisTemplate
            .opsForList()
            .range(AUDIT_LOG_KEY, 0, -1)
            .map { objectMapper.readValue(it, AuditLog::class.java) }
            .filter(filter::matches)
    }

    companion object {
        const val AUDIT_LOG_KEY = Namespaced.SYSTEM + ":audit:log"
    }
}
