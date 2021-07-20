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

import com.google.common.base.Strings;
import com.google.common.primitives.Ints;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.cosky.core.redis.RedisScripts;
import me.ahoo.cosky.discovery.*;
import me.ahoo.cosky.core.listener.MessageListenable;
import me.ahoo.cosky.core.listener.MessageListener;
import me.ahoo.cosky.core.listener.PatternTopic;
import me.ahoo.cosky.core.listener.Topic;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author ahoo wang
 */
@Slf4j
public class RedisServiceStatistic implements ServiceStatistic {
    private final RedisClusterAsyncCommands<String, String> redisCommands;
    private final MessageListenable messageListenable;
    private final InstanceListener instanceListener;
    private final ConcurrentHashMap<String, Object> listenedNamespaces = new ConcurrentHashMap<>();
    private final static Object NONE = new Object();

    public RedisServiceStatistic(
            RedisClusterAsyncCommands<String, String> redisCommands, MessageListenable messageListenable) {
        this.redisCommands = redisCommands;
        this.messageListenable = messageListenable;
        this.instanceListener = new InstanceListener();
    }

    private void startListeningServiceInstancesOfNamespace(String namespace) {
        listenedNamespaces.computeIfAbsent(namespace, ns -> {
            var instancePattern = DiscoveryKeyGenerator.getInstanceKeyPatternOfNamespace(namespace);
            var instanceTopic = PatternTopic.of(instancePattern);
            messageListenable.addListener(instanceTopic, instanceListener);
            return NONE;
        });
    }

    @Override
    public CompletableFuture<Void> statService(String namespace) {
        startListeningServiceInstancesOfNamespace(namespace);
        return statService0(namespace, null);
    }

    @Override
    public CompletableFuture<Void> statService(String namespace, String serviceId) {
        return statService0(namespace, serviceId);
    }

    private CompletableFuture<Void> statService0(String namespace, String serviceId) {
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
                redisCommands.evalsha(sha, ScriptOutputType.STATUS, keys, values));
    }

    public CompletableFuture<Long> countService(String namespace) {
        var serviceIdxStatKey = DiscoveryKeyGenerator.getServiceStatKey(namespace);
        return redisCommands.hlen(serviceIdxStatKey).toCompletableFuture();
    }

    @Override
    public CompletableFuture<List<ServiceStat>> getServiceStats(String namespace) {
        var serviceIdxStatKey = DiscoveryKeyGenerator.getServiceStatKey(namespace);
        return redisCommands.hgetall(serviceIdxStatKey).thenApply(statMap -> statMap.entrySet().stream().map(stat -> {
            ServiceStat serviceStat = new ServiceStat();
            serviceStat.setServiceId(stat.getKey());
            serviceStat.setInstanceCount(Ints.tryParse(stat.getValue()));
            return serviceStat;
        }).collect(Collectors.toList())).toCompletableFuture();
    }

    @Override
    public CompletableFuture<Long> getInstanceCount(String namespace) {
        return DiscoveryRedisScripts.loadInstanceCountStat(redisCommands).
                thenCompose(sha -> redisCommands.evalsha(sha, ScriptOutputType.INTEGER, namespace));
    }


    public static final String SERVICE_TOPOLOGY_GET = "service_topology_get.lua";

    @Override
    public CompletableFuture<Map<String, Set<String>>> getTopology(String namespace) {
        return RedisScripts.doEnsureScript(SERVICE_TOPOLOGY_GET, redisCommands,
                sha -> redisCommands.evalsha(sha, ScriptOutputType.MULTI, namespace))
                .thenApply(result -> {
                    Map<String, Set<String>> topology = new HashMap<>();
                    List<Object> deps = (List<Object>) result;
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
        public void onMessage(Topic topic, String channel, String message) {
            if (log.isInfoEnabled()) {
                log.info("onMessage@InstanceListener - topic:[{}] - channel:[{}] - message:[{}]", topic, channel, message);
            }

            if (ServiceChangedEvent.RENEW.equals(message)) {
                return;
            }

            var instanceKey = channel;
            var namespace = DiscoveryKeyGenerator.getNamespaceOfKey(instanceKey);
            var instanceId = DiscoveryKeyGenerator.getInstanceIdOfKey(namespace, instanceKey);
            var instance = InstanceIdGenerator.DEFAULT.of(instanceId);
            var serviceId = instance.getServiceId();
            statService0(namespace, serviceId);
        }
    }
}
