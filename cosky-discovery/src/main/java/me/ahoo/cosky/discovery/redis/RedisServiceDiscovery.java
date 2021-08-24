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

package me.ahoo.cosky.discovery.redis;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.cosky.core.NamespacedContext;
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator;
import me.ahoo.cosky.discovery.ServiceDiscovery;
import me.ahoo.cosky.discovery.ServiceInstance;
import me.ahoo.cosky.discovery.ServiceInstanceCodec;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author ahoo wang
 */
@Slf4j
public class RedisServiceDiscovery implements ServiceDiscovery {
    private final RedisClusterReactiveCommands<String, String> redisCommands;

    public RedisServiceDiscovery(
            RedisClusterReactiveCommands<String, String> redisCommands) {
        this.redisCommands = redisCommands;
    }

    @Override
    public Mono<List<ServiceInstance>> getInstances(String serviceId) {
        return getInstances(NamespacedContext.GLOBAL.getRequiredNamespace(), serviceId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<List<ServiceInstance>> getInstances(String namespace, String serviceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceId), "serviceId can not be empty!");

        return DiscoveryRedisScripts.doDiscoveryGetInstances(redisCommands, sha -> {
            String[] keys = {namespace};
            String[] values = {serviceId};
            return redisCommands.evalsha(sha, ScriptOutputType.MULTI, keys, values)
                    .map(instanceGroups -> {
                        List<List<String>> groups = (List<List<String>>) instanceGroups;
                        if (Objects.isNull(instanceGroups)) {
                            return Collections.<ServiceInstance>emptyList();
                        }
                        List<ServiceInstance> instances = new ArrayList<>(groups.size());
                        groups.forEach(instanceData -> instances.add(ServiceInstanceCodec.decode(instanceData)));
                        return instances;
                    }).last();
        });

    }


    @Override
    public Mono<ServiceInstance> getInstance(String namespace, String serviceId, String instanceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceId), "serviceId can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(instanceId), "instanceId can not be empty!");

        return DiscoveryRedisScripts.doDiscoveryGetInstance(redisCommands, sha -> {
            String[] keys = {namespace};
            String[] values = {serviceId, instanceId};
            return redisCommands.evalsha(sha, ScriptOutputType.MULTI, keys, values)
                    .map(record -> (List<String>) record)
                    .next()
                    .mapNotNull(instanceData -> {
                        if (instanceData.isEmpty()) {
                            return null;
                        }
                        return ServiceInstanceCodec.decode(instanceData);
                    });
        });

    }

    @Override
    public Mono<Long> getInstanceTtl(String namespace, String serviceId, String instanceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceId), "serviceId can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(instanceId), "instanceId can not be empty!");

        return DiscoveryRedisScripts.doDiscoveryGetInstanceTtl(redisCommands, sha -> {
            String[] keys = {namespace};
            String[] values = {serviceId, instanceId};
            return redisCommands.evalsha(sha, ScriptOutputType.INTEGER, keys, values)
                    .cast(Long.class)
                    .next();
        });
    }

    @Override
    public Mono<List<String>> getServices(String namespace) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");

        var serviceIdxKey = DiscoveryKeyGenerator.getServiceIdxKey(namespace);
        return redisCommands.smembers(serviceIdxKey).collectList();
    }

}
