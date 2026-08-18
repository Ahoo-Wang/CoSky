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

import io.swagger.v3.oas.annotations.tags.Tag
import me.ahoo.cosky.rest.support.RequestPathPrefix
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets
import java.time.Instant

/**
 * Audit Log Controller.
 *
 * @author ahoo wang
 */
@RestController
@RequestMapping(RequestPathPrefix.AUDIT_LOG_PREFIX)
@Tag(name = "AuditLog")
class AuditLogController(private val auditService: AuditLogService) {
    @GetMapping
    fun queryLog(
        @RequestParam(defaultValue = "0") offset: Long,
        @RequestParam(defaultValue = "10") limit: Long,
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) from: Long?,
        @RequestParam(required = false) to: Long?,
        @RequestParam(required = false) successful: Boolean?
    ): Mono<QueryLogResponse> {
        return auditService.queryLog(offset, limit, AuditLogFilter(query, from, to, successful))
    }

    @GetMapping("/export", produces = ["text/csv"])
    fun exportLog(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) from: Long?,
        @RequestParam(required = false) to: Long?,
        @RequestParam(required = false) successful: Boolean?
    ): Mono<ResponseEntity<ByteArray>> {
        return auditService.queryAll(AuditLogFilter(query, from, to, successful))
            .map { log ->
                listOf(
                    Instant.ofEpochMilli(log.opTime),
                    log.operator,
                    log.ip,
                    log.resource,
                    log.action,
                    log.status,
                    log.msg,
                )
                    .joinToString(",") { csvCell(it.toString()) }
            }
            .startWith("Timestamp,Operator,Client IP,Resource,Action,Status,Message")
            .collectList()
            .map { rows ->
                val headers = HttpHeaders()
                headers.add(
                    "Content-Disposition",
                    "attachment;filename=cosky_audit_log_${System.currentTimeMillis()}.csv",
                )
                headers.contentType = MediaType(
                    "text",
                    "csv",
                    StandardCharsets.UTF_8,
                )
                ResponseEntity(
                    rows.joinToString("\n").toByteArray(StandardCharsets.UTF_8),
                    headers,
                    HttpStatus.OK,
                )
            }
    }

    companion object {
        internal fun csvCell(value: String): String {
            val safeValue = when (value.firstOrNull()) {
                '=', '+', '-', '@' -> "'$value"
                else -> value
            }
            return "\"${safeValue.replace("\"", "\"\"")}\""
        }
    }
}
