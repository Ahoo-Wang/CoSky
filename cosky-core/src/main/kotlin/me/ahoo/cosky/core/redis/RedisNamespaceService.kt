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
package me.ahoo.cosky.core.redis

import me.ahoo.cosky.core.NamespaceService
import me.ahoo.cosky.core.Namespaced
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Redis Namespace Service.
 *
 * @author ahoo wang
 */
class RedisNamespaceService(private val redisTemplate: ReactiveStringRedisTemplate) : NamespaceService {
    companion object {
        const val NAMESPACE_IDX_KEY = "${Namespaced.SYSTEM}:ns_idx"
    }

    override val namespaces: Flux<String>
        get() = redisTemplate
            .opsForSet()
            .members(NAMESPACE_IDX_KEY)

    override fun setNamespace(namespace: String): Mono<Boolean> {
        return Mono.defer {
            ensureNamespace(namespace)
            redisTemplate
                .opsForSet()
                .add(NAMESPACE_IDX_KEY, namespace)
                .map { it > 0 }
        }
    }

    private fun ensureNamespace(namespace: String) {
        require(namespace.isNotBlank()) { "namespace can not be blank!" }
    }

    override fun removeNamespace(namespace: String): Mono<Boolean> {
        return Mono.defer {
            ensureNamespace(namespace)
            redisTemplate
                .opsForSet()
                .remove(NAMESPACE_IDX_KEY, namespace)
                .map { it > 0 }
        }
    }
}
