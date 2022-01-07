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

package me.ahoo.cosky.core.redis;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands;
import me.ahoo.cosky.core.NamespaceService;
import me.ahoo.cosky.core.Namespaced;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author ahoo wang
 */
public class RedisNamespaceService implements NamespaceService {

    private final static String NAMESPACE_IDX_KEY = Namespaced.SYSTEM + ":ns_idx";

    private final RedisClusterReactiveCommands<String, String> redisCommands;

    public RedisNamespaceService(RedisClusterReactiveCommands<String, String> redisCommands) {
        this.redisCommands = redisCommands;
    }

    @Override
    public Mono<Set<String>> getNamespaces() {
        return redisCommands.smembers(NAMESPACE_IDX_KEY).collect(Collectors.toSet());
    }

    @Override
    public Mono<Boolean> setNamespace(String namespace) {
        ensureNamespace(namespace);

        return redisCommands.sadd(NAMESPACE_IDX_KEY, namespace)
                .map(affected -> affected > 0);
    }

    private void ensureNamespace(String namespace) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
    }

    @Override
    public Mono<Boolean> removeNamespace(String namespace) {
        ensureNamespace(namespace);

        return redisCommands.srem(NAMESPACE_IDX_KEY, namespace)
                .map(affected -> affected > 0);
    }

}
