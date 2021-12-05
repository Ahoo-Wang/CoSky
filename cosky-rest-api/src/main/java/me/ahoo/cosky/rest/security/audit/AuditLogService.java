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

package me.ahoo.cosky.rest.security.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands;
import lombok.SneakyThrows;
import me.ahoo.cosid.CosIdException;
import me.ahoo.cosky.core.CoskyException;
import me.ahoo.cosky.core.Namespaced;
import me.ahoo.cosky.core.redis.RedisConnectionFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author ahoo wang
 */
@Service
public class AuditLogService {

    public static final String AUDIT_LOG_KEY = Namespaced.SYSTEM + ":audit:log";

    private final ObjectMapper objectMapper;
    private final RedisClusterReactiveCommands<String, String> redisCommands;

    public AuditLogService(ObjectMapper objectMapper, RedisConnectionFactory redisConnectionFactory) {
        this.objectMapper = objectMapper;
        this.redisCommands = redisConnectionFactory.getShareReactiveCommands();
    }

    public Mono<Long> addLog(AuditLog log) {
        try {
            String logStr = this.objectMapper.writeValueAsString(log);
            return redisCommands.lpush(AUDIT_LOG_KEY, logStr);
        } catch (JsonProcessingException e) {
            throw new CoskyException(e);
        }
    }

    public Mono<List<AuditLog>> queryLog(long offset, long limit) {
        return this.redisCommands.lrange(AUDIT_LOG_KEY, offset, offset + limit - 1)
                .map(logStr -> {
                    try {
                        return this.objectMapper.readValue(logStr, AuditLog.class);
                    } catch (JsonProcessingException e) {
                        throw new CoskyException(e);
                    }
                })
                .collect(Collectors.toList());
    }

    public Mono<Long> getTotal() {
        return this.redisCommands.llen(AUDIT_LOG_KEY);
    }
}
