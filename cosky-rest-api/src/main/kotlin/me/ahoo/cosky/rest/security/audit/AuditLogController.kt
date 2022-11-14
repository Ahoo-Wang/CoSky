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

import me.ahoo.cosky.rest.security.annotation.AdminResource
import me.ahoo.cosky.rest.support.RequestPathPrefix
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * Audit Log Controller.
 *
 * @author ahoo wang
 */
@CrossOrigin("*")
@RestController
@RequestMapping(RequestPathPrefix.AUDIT_LOG_PREFIX)
@AdminResource
class AuditLogController(private val auditService: AuditLogService) {
    @GetMapping
    fun queryLog(offset: Long, limit: Long): Mono<QueryLogResponse> {
        return Mono.zip(
            auditService.total,
            auditService.queryLog(offset, limit)
        )
            .map { QueryLogResponse(it.t2, it.t1) }
    }
}
