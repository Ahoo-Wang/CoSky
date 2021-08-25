/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

package me.ahoo.cosky.rest.controller;

import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosky.rest.dto.auditlog.QueryLogResponse;
import me.ahoo.cosky.rest.security.audit.AuditLogService;
import me.ahoo.cosky.rest.security.audit.AuditLog;
import me.ahoo.cosky.rest.security.annotation.AdminResource;
import me.ahoo.cosky.rest.support.RequestPathPrefix;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
@CrossOrigin("*")
@RestController
@RequestMapping(RequestPathPrefix.AUDIT_LOG_PREFIX)
@Slf4j
@AdminResource
public class AuditLogController {
    private final AuditLogService auditService;

    public AuditLogController(AuditLogService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public Mono<QueryLogResponse> queryLog(long offset, long limit) {
        return Mono.zip(auditService.getTotal(), auditService.queryLog(offset, limit))
                .map(tuple -> {
                    QueryLogResponse queryLogResponse = new QueryLogResponse();
                    queryLogResponse.setTotal(tuple.getT1());
                    queryLogResponse.setList(tuple.getT2());
                    return queryLogResponse;
                });
    }
}
