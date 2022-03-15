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

package me.ahoo.cosky.discovery.redis;

import me.ahoo.cosky.core.NamespacedContext;
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator;
import me.ahoo.cosky.discovery.ServiceDiscovery;
import me.ahoo.cosky.discovery.ServiceInstance;
import me.ahoo.cosky.discovery.ServiceInstanceCodec;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Redis Service Discovery.
 *
 * @author ahoo wang
 */
@Slf4j
public class RedisServiceDiscovery implements ServiceDiscovery {
    private final ReactiveStringRedisTemplate redisTemplate;
    
    public RedisServiceDiscovery(
        ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @Override
    public Flux<ServiceInstance> getInstances(String serviceId) {
        return getInstances(NamespacedContext.GLOBAL.getRequiredNamespace(), serviceId);
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public Flux<ServiceInstance> getInstances(String namespace, String serviceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceId), "serviceId can not be empty!");
        
        
        return redisTemplate.execute(
                DiscoveryRedisScripts.SCRIPT_REGISTRY_GET_INSTANCES,
                Collections.singletonList(namespace),
                Collections.singletonList(serviceId)
            )
            .flatMapIterable(instanceGroups -> {
                List<List<String>> groups = (List<List<String>>) instanceGroups;
                if (Objects.isNull(instanceGroups)) {
                    return Collections.<ServiceInstance>emptyList();
                }
                List<ServiceInstance> instances = new ArrayList<>(groups.size());
                groups.forEach(instanceData -> instances.add(ServiceInstanceCodec.decode(instanceData)));
                return instances;
            });
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public Mono<ServiceInstance> getInstance(String namespace, String serviceId, String instanceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceId), "serviceId can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(instanceId), "instanceId can not be empty!");
        
        return redisTemplate.execute(
                DiscoveryRedisScripts.SCRIPT_REGISTRY_GET_INSTANCE,
                Collections.singletonList(namespace),
                Arrays.asList(serviceId, instanceId)
            )
            .map(record -> (List<String>) record)
            .mapNotNull(instanceData -> {
                if (instanceData.isEmpty()) {
                    return null;
                }
                return ServiceInstanceCodec.decode(instanceData);
            })
            .next();
    }
    
    @Override
    public Mono<Long> getInstanceTtl(String namespace, String serviceId, String instanceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceId), "serviceId can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(instanceId), "instanceId can not be empty!");
        return redisTemplate.execute(
                DiscoveryRedisScripts.SCRIPT_REGISTRY_GET_INSTANCE_TTL,
                Collections.singletonList(namespace),
                Arrays.asList(serviceId, instanceId)
            )
            .next();
    }
    
    @Override
    public Flux<String> getServices(String namespace) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        
        String serviceIdxKey = DiscoveryKeyGenerator.getServiceIdxKey(namespace);
        return redisTemplate
            .opsForSet()
            .members(serviceIdxKey);
    }
    
}
