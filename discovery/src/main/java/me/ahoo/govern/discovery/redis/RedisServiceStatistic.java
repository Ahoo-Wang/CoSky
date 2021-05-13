package me.ahoo.govern.discovery.redis;

import com.google.common.base.Strings;
import com.google.common.primitives.Ints;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.govern.core.listener.MessageListenable;
import me.ahoo.govern.core.listener.MessageListener;
import me.ahoo.govern.core.listener.PatternTopic;
import me.ahoo.govern.core.listener.Topic;
import me.ahoo.govern.discovery.*;

import java.util.List;
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
        return DiscoveryRedisScripts.loadServiceStat(redisCommands).thenCompose(sha ->
                redisCommands.evalsha(sha, ScriptOutputType.STATUS, keys, values)
        );
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

    private class InstanceListener implements MessageListener {

        @Override
        public void onMessage(Topic topic, String channel, String message) {
            if (log.isInfoEnabled()) {
                log.info("onMessage@InstanceListener - topic:[{}] - channel:[{}] - message:[{}]", topic, channel, message);
            }

            if (ServiceChangedListener.RENEW.equals(message)) {
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
