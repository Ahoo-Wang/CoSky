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

    fun queryLog(offset: Long, limit: Long): Mono<List<AuditLog>> {
        return redisTemplate
            .opsForList()
            .range(AUDIT_LOG_KEY, offset, offset + limit - 1)
            .map {
                objectMapper.readValue(it, AuditLog::class.java)
            }.collectList()
    }

    val total: Mono<Long>
        get() = redisTemplate
            .opsForList()
            .size(AUDIT_LOG_KEY)

    companion object {
        const val AUDIT_LOG_KEY = Namespaced.SYSTEM + ":audit:log"
    }
}
