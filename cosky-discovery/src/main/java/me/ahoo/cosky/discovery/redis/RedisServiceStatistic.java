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

import me.ahoo.cosky.discovery.DiscoveryKeyGenerator;
import me.ahoo.cosky.discovery.Instance;
import me.ahoo.cosky.discovery.InstanceIdGenerator;
import me.ahoo.cosky.discovery.ServiceChangedEvent;
import me.ahoo.cosky.discovery.ServiceStat;
import me.ahoo.cosky.discovery.ServiceStatistic;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.primitives.Ints;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis Service Statistic.
 *
 * @author ahoo wang
 */
@Slf4j
public class RedisServiceStatistic implements ServiceStatistic {
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ReactiveRedisMessageListenerContainer listenerContainer;
    private final ConcurrentHashMap<String, Disposable> listenedNamespaces = new ConcurrentHashMap<>();
    
    public RedisServiceStatistic(
        ReactiveStringRedisTemplate redisTemplate, ReactiveRedisMessageListenerContainer listenerContainer) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
    }
    
    private void startListeningServiceInstancesOfNamespace(String namespace) {
        listenedNamespaces.computeIfAbsent(namespace, ns -> {
            String instancePattern = DiscoveryKeyGenerator.getInstanceKeyPatternOfNamespace(namespace);
            return listenerContainer.receive(PatternTopic.of(instancePattern))
                .doOnNext(this::instanceChanged).subscribe();
        });
    }
    
    
    private void instanceChanged(ReactiveSubscription.PatternMessage<String, String, String> message) {
        if (log.isInfoEnabled()) {
            log.info("instanceChanged - pattern:[{}] - channel:[{}] - message:[{}]", message.getPattern(), message.getChannel(), message.getMessage());
        }
        
        if (ServiceChangedEvent.RENEW.equals(message.getMessage())) {
            return;
        }
        
        String instanceKey = message.getChannel();
        String namespace = DiscoveryKeyGenerator.getNamespaceOfKey(instanceKey);
        String instanceId = DiscoveryKeyGenerator.getInstanceIdOfKey(namespace, instanceKey);
        Instance instance = InstanceIdGenerator.DEFAULT.of(instanceId);
        String serviceId = instance.getServiceId();
        statService0(namespace, serviceId).subscribe();
    }
    
    @Override
    public Mono<Void> statService(String namespace) {
        startListeningServiceInstancesOfNamespace(namespace);
        return statService0(namespace, null);
    }
    
    @Override
    public Mono<Void> statService(String namespace, String serviceId) {
        return statService0(namespace, serviceId);
    }
    
    private Mono<Void> statService0(String namespace, @Nullable String serviceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        
        if (log.isInfoEnabled()) {
            log.info("statService  @ namespace:[{}].", namespace);
        }
        String[] values;
        if (!Strings.isNullOrEmpty(serviceId)) {
            values = new String[] {serviceId};
        } else {
            values = new String[] {};
        }
        
        return redisTemplate.execute(
                DiscoveryRedisScripts.SCRIPT_SERVICE_STAT,
                Collections.singletonList(namespace),
                Arrays.asList(values)
            )
            .then();
    }
    
    public Mono<Long> countService(String namespace) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        
        String serviceIdxStatKey = DiscoveryKeyGenerator.getServiceStatKey(namespace);
        return redisTemplate
            .opsForHash()
            .size(serviceIdxStatKey);
    }
    
    @Override
    public Flux<ServiceStat> getServiceStats(String namespace) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        
        String serviceIdxStatKey = DiscoveryKeyGenerator.getServiceStatKey(namespace);
        return redisTemplate
            .<String, String>opsForHash().entries(serviceIdxStatKey)
            .map(stat -> {
                ServiceStat serviceStat = new ServiceStat();
                serviceStat.setServiceId(stat.getKey());
                serviceStat.setInstanceCount(Ints.tryParse(stat.getValue()));
                return serviceStat;
            });
    }
    
    @Override
    public Mono<Long> getInstanceCount(String namespace) {
        
        return redisTemplate.execute(
                DiscoveryRedisScripts.SCRIPT_INSTANCE_COUNT_STAT,
                Collections.singletonList(namespace)
            )
            .next();
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public Mono<Map<String, Set<String>>> getTopology(String namespace) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        
        return redisTemplate.execute(
                DiscoveryRedisScripts.SCRIPT_SERVICE_TOPOLOGY_GET,
                Collections.singletonList(namespace)
            )
            .map(result -> {
                List<List<Object>> deps = (List<List<Object>>) result;
                Map<String, Set<String>> topology = new HashMap<>(deps.size());
                String consumerName = "";
                for (Object dep : deps) {
                    if (dep instanceof String) {
                        consumerName = dep.toString();
                    }
                    if (dep instanceof List) {
                        topology.put(consumerName, new HashSet<>((List<String>) dep));
                    }
                }
                return topology;
            })
            .next();
    }
}
