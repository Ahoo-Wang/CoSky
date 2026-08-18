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
        require(limit in 1..MAX_PAGE_SIZE) { "limit must be between 1 and $MAX_PAGE_SIZE." }
        require(offset <= Long.MAX_VALUE - (limit - 1)) { "offset and limit are too large." }
        if (filter.isEmpty) {
            return Mono.zip(
                redisTemplate.opsForList().size(AUDIT_LOG_KEY),
                redisTemplate.opsForList()
                    .range(AUDIT_LOG_KEY, offset, offset + limit - 1)
                    .map { objectMapper.readValue(it, AuditLog::class.java) }
                    .collectList(),
            ).map { QueryLogResponse(it.t2, it.t1) }
        }
        return filteredLogs(filter).publish { logs ->
            Mono.zip(
                logs.skip(offset).take(limit).collectList(),
                logs.count(),
            ).map {
                QueryLogResponse(it.t1, it.t2)
            }
        }.next()
    }

    fun queryAll(filter: AuditLogFilter): Flux<AuditLog> = filteredLogs(filter)

    private fun filteredLogs(filter: AuditLogFilter): Flux<AuditLog> {
        return redisTemplate.opsForList().size(AUDIT_LOG_KEY)
            .flatMapMany { snapshotSize ->
                if (snapshotSize == 0L) {
                    Flux.empty()
                } else {
                    readBatch(snapshotSize, 0)
                        // ponytail: filtering remains O(n); batches bound memory until indexed audit storage is justified.
                        .expand { batch ->
                            if (batch.nextOffset >= snapshotSize) {
                                Mono.empty()
                            } else {
                                readBatch(snapshotSize, batch.nextOffset)
                            }
                        }
                }
            }
            .concatMapIterable { it.logs }
            .filter(filter::matches)
    }

    private fun readBatch(snapshotSize: Long, offset: Long): Mono<AuditLogBatch> {
        val start = -snapshotSize + offset
        val end = minOf(-1, start + READ_BATCH_SIZE - 1)
        return redisTemplate
            .opsForList()
            .range(AUDIT_LOG_KEY, start, end)
            .map { objectMapper.readValue(it, AuditLog::class.java) }
            .collectList()
            .map { AuditLogBatch(minOf(snapshotSize, offset + READ_BATCH_SIZE), it) }
    }

    private data class AuditLogBatch(val nextOffset: Long, val logs: List<AuditLog>)

    companion object {
        const val AUDIT_LOG_KEY = Namespaced.SYSTEM + ":audit:log"
        private const val READ_BATCH_SIZE = 500
        private const val MAX_PAGE_SIZE = 1000L
    }
}
