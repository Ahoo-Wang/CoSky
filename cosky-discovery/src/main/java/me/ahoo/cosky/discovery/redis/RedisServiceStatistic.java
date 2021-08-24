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
import com.google.common.primitives.Ints;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.cosky.core.redis.RedisScripts;
import me.ahoo.cosky.discovery.*;
import me.ahoo.cosky.core.listener.MessageListenable;
import me.ahoo.cosky.core.listener.MessageListener;
import me.ahoo.cosky.core.listener.Topic;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author ahoo wang
 */
@Slf4j
public class RedisServiceStatistic implements ServiceStatistic {
    private final RedisClusterReactiveCommands<String, String> redisCommands;
    private final MessageListenable messageListenable;
    private final InstanceListener instanceListener;
    private final ConcurrentHashMap<String, Object> listenedNamespaces = new ConcurrentHashMap<>();
    private final static Object NONE = new Object();

    public RedisServiceStatistic(
            RedisClusterReactiveCommands<String, String> redisCommands, MessageListenable messageListenable) {
        this.redisCommands = redisCommands;
        this.messageListenable = messageListenable;
        this.instanceListener = new InstanceListener();
    }

    private void startListeningServiceInstancesOfNamespace(String namespace) {
        listenedNamespaces.computeIfAbsent(namespace, ns -> {
            var instancePattern = DiscoveryKeyGenerator.getInstanceKeyPatternOfNamespace(namespace);
            messageListenable.addPatternListener(instancePattern, instanceListener);
            return NONE;
        });
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
        String[] keys = {namespace};
        String[] values;
        if (!Strings.isNullOrEmpty(serviceId)) {
            values = new String[]{serviceId};
        } else {
            values = new String[]{};
        }
        return DiscoveryRedisScripts.doServiceStat(redisCommands, sha ->
                redisCommands.evalsha(sha, ScriptOutputType.STATUS, keys, values).then());
    }

    public Mono<Long> countService(String namespace) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");

        var serviceIdxStatKey = DiscoveryKeyGenerator.getServiceStatKey(namespace);
        return redisCommands.hlen(serviceIdxStatKey);
    }

    @Override
    public Mono<List<ServiceStat>> getServiceStats(String namespace) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");

        var serviceIdxStatKey = DiscoveryKeyGenerator.getServiceStatKey(namespace);
        return redisCommands.hgetall(serviceIdxStatKey)
                .map(stat -> {
                    ServiceStat serviceStat = new ServiceStat();
                    serviceStat.setServiceId(stat.getKey());
                    serviceStat.setInstanceCount(Ints.tryParse(stat.getValue()));
                    return serviceStat;
                }).collect(Collectors.toList());
    }

    @Override
    public Mono<Long> getInstanceCount(String namespace) {
        return DiscoveryRedisScripts.loadInstanceCountStat(redisCommands).
                flatMap(sha -> redisCommands.evalsha(sha, ScriptOutputType.INTEGER, namespace)
                        .cast(Long.class)
                        .next()
                );
    }


    public static final String SERVICE_TOPOLOGY_GET = "service_topology_get.lua";

    @Override
    public Mono<Map<String, Set<String>>> getTopology(String namespace) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");

        return RedisScripts.doEnsureScript(SERVICE_TOPOLOGY_GET, redisCommands,
                        sha -> redisCommands.evalsha(sha, ScriptOutputType.MULTI, namespace).next())
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
                });
    }

    private class InstanceListener implements MessageListener {

        @Override
        public void onMessage(@Nullable String pattern, String channel, String message) {
            if (log.isInfoEnabled()) {
                log.info("onMessage@InstanceListener - pattern:[{}] - channel:[{}] - message:[{}]", pattern, channel, message);
            }

            if (ServiceChangedEvent.RENEW.equals(message)) {
                return;
            }

            String instanceKey = channel;
            String namespace = DiscoveryKeyGenerator.getNamespaceOfKey(instanceKey);
            String instanceId = DiscoveryKeyGenerator.getInstanceIdOfKey(namespace, instanceKey);
            Instance instance = InstanceIdGenerator.DEFAULT.of(instanceId);
            String serviceId = instance.getServiceId();
            statService0(namespace, serviceId).subscribe();
        }
    }
}
