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

package me.ahoo.cosky.rest.security.audit;

import me.ahoo.cosky.core.CoSkyException;
import me.ahoo.cosky.core.Namespaced;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Audit Log Service.
 *
 * @author ahoo wang
 */
@Service
public class AuditLogService {
    
    public static final String AUDIT_LOG_KEY = Namespaced.SYSTEM + ":audit:log";
    
    private final ObjectMapper objectMapper;
    private final ReactiveStringRedisTemplate redisTemplate;
    
    public AuditLogService(ObjectMapper objectMapper, ReactiveStringRedisTemplate redisTemplate) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }
    
    public Mono<Long> addLog(AuditLog log) {
        try {
            String logStr = this.objectMapper.writeValueAsString(log);
            return redisTemplate.opsForList()
                .leftPush(AUDIT_LOG_KEY, logStr);
        } catch (JsonProcessingException e) {
            throw new CoSkyException(e);
        }
    }
    
    public Mono<List<AuditLog>> queryLog(long offset, long limit) {
        return redisTemplate
            .opsForList()
            .range(AUDIT_LOG_KEY, offset, offset + limit - 1)
            .map(logStr -> {
                try {
                    return this.objectMapper.readValue(logStr, AuditLog.class);
                } catch (JsonProcessingException e) {
                    throw new CoSkyException(e);
                }
            })
            .collect(Collectors.toList());
    }
    
    public Mono<Long> getTotal() {
        return redisTemplate
            .opsForList()
            .size(AUDIT_LOG_KEY);
    }
}
