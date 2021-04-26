package me.ahoo.govern.discovery.redis;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.govern.core.listener.*;
import me.ahoo.govern.core.util.RedisKeySpaces;
import me.ahoo.govern.discovery.InstanceIdGenerator;
import me.ahoo.govern.discovery.DiscoveryKeyGenerator;
import me.ahoo.govern.discovery.ServiceDiscovery;
import me.ahoo.govern.discovery.ServiceInstance;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * @author ahoo wang
 */
@Slf4j
public class ConsistencyRedisServiceDiscovery implements ServiceDiscovery {

    private final ServiceDiscovery delegate;
    private final MessageListenable messageListenable;
    private final ServiceIdxListener serviceIdxListener;
    private final InstanceListener instanceListener;

    private final DiscoveryKeyGenerator keyGenerator;
    private final Topic serviceIdxTopic;

    private final ConcurrentHashMap<String, CompletableFuture<List<ServiceInstance>>> serviceMapInstances;
    private volatile CompletableFuture<Set<String>> servicesFuture = null;

    public ConsistencyRedisServiceDiscovery(DiscoveryKeyGenerator keyGenerator, ServiceDiscovery delegate, MessageListenable messageListenable) {
        var topicStr = RedisKeySpaces.getTopicOfKey(keyGenerator.getServiceIdxKey());
        serviceIdxTopic = ChannelTopic.of(topicStr);
        this.keyGenerator = keyGenerator;
        this.serviceMapInstances = new ConcurrentHashMap<>();
        this.delegate = delegate;
        this.messageListenable = messageListenable;
        this.serviceIdxListener = new ServiceIdxListener();
        this.instanceListener = new InstanceListener();
    }

    @Override
    public CompletableFuture<Set<String>> getServices() {
        if (Objects.nonNull(servicesFuture)) {
            return servicesFuture;
        }
        synchronized (this) {
            if (Objects.nonNull(servicesFuture)) {
                return servicesFuture;
            }
            servicesFuture = messageListenable.addListener(serviceIdxTopic, serviceIdxListener)
                    .thenCompose((nil) -> servicesFuture = delegate.getServices());
        }
        return servicesFuture;
    }

    @Override
    public CompletableFuture<List<ServiceInstance>> getInstances(String serviceId) {
        return serviceMapInstances.computeIfAbsent(serviceId, (_serviceId) -> addListener(serviceId).
                thenCompose(nil -> delegate.getInstances(serviceId)));
    }

    @VisibleForTesting
    public CompletableFuture<Void> addListener(String serviceId) {
        PatternTopic instanceTopic = getPatternTopic(serviceId);
        return messageListenable.addListener(instanceTopic, instanceListener);
    }

    @VisibleForTesting
    public Future<Void> removeListener(String serviceId) {
        PatternTopic instanceTopic = getPatternTopic(serviceId);
        return messageListenable.removeListener(instanceTopic);
    }

    private PatternTopic getPatternTopic(String serviceId) {
        var instancePattern = keyGenerator.getInstanceKeyPatternOfService(serviceId);
        var topicStr = RedisKeySpaces.getTopicOfKey(instancePattern);
        var instanceTopic = PatternTopic.of(topicStr);
        return instanceTopic;
    }

    private class ServiceIdxListener implements MessageListener {

        @Override
        public void onMessage(Topic topic, String channel, String message) {
            if (log.isInfoEnabled()) {
                log.info("onMessage@ServiceIdxListener - topic:[{}] - channel:[{}] - message:[{}]", topic, channel, message);
            }

            servicesFuture = delegate.getServices();
        }
    }

    private class InstanceListener implements MessageListener {

        private final static String MSG_EXPIRE = "expire";

        @Override
        public void onMessage(Topic topic, String channel, String message) {
            if (log.isInfoEnabled()) {
                log.info("onMessage@InstanceListener - topic:[{}] - channel:[{}] - message:[{}]", topic, channel, message);
            }

            if (MSG_EXPIRE.equals(message)) {
                /**
                 * 忽略 {@link #MSG_EXPIRE} 消息
                 */
                return;
            }
            var key = RedisKeySpaces.getKeyOfChannel(channel);
            var instanceId = keyGenerator.getInstanceIdOfKey(key);
            var instance = InstanceIdGenerator.DEFAULT.of(instanceId);
            var serviceId = instance.getServiceId();
            /**
             * TODO 目前使用的是相应ServiceId的实例整体替换，按照 message :[del,set,expire] 分解,单个实例替换
             */
            serviceMapInstances.put(serviceId, delegate.getInstances(serviceId));
        }
    }
}
