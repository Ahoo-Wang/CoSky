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

package me.ahoo.cosky.core.redis;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import me.ahoo.cosky.core.NamespaceService;
import me.ahoo.cosky.core.Namespaced;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
public class RedisNamespaceService implements NamespaceService {

    private final static String NAMESPACE_IDX_KEY = Namespaced.SYSTEM + ":ns_idx";

    private final RedisClusterAsyncCommands<String, String> redisCommands;

    public RedisNamespaceService(RedisClusterAsyncCommands<String, String> redisCommands) {
        this.redisCommands = redisCommands;
    }

    @Override
    public CompletableFuture<Set<String>> getNamespaces() {
        return redisCommands.smembers(NAMESPACE_IDX_KEY).toCompletableFuture();
    }

    @Override
    public CompletableFuture<Boolean> setNamespace(String namespace) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");

        return redisCommands.sadd(NAMESPACE_IDX_KEY, namespace)
                .thenApply(affected -> affected > 0)
                .toCompletableFuture();
    }

    @Override
    public CompletableFuture<Boolean> removeNamespace(String namespace) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");

        return redisCommands.srem(NAMESPACE_IDX_KEY, namespace).thenApply(affected -> affected > 0).toCompletableFuture();
    }

}
