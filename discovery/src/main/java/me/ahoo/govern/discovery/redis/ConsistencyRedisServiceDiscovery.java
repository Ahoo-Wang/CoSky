package me.ahoo.govern.discovery.redis;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.govern.core.NamespacedContext;
import me.ahoo.govern.core.listener.*;
import me.ahoo.govern.discovery.*;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * @author ahoo wang
 */
@Slf4j
public class ConsistencyRedisServiceDiscovery implements ServiceDiscovery {

    private final ServiceDiscovery delegate;
    private final MessageListenable messageListenable;
    private final ServiceIdxListener serviceIdxListener;
    private final InstanceListener instanceListener;

    private final ConcurrentHashMap<NamespacedServiceId, CompletableFuture<List<ServiceInstance>>> serviceMapInstances;
    private final ConcurrentHashMap<String, CompletableFuture<Set<String>>> namespaceMapServices;

    public ConsistencyRedisServiceDiscovery(ServiceDiscovery delegate, MessageListenable messageListenable) {
        this.serviceMapInstances = new ConcurrentHashMap<>();
        this.namespaceMapServices = new ConcurrentHashMap<>();
        this.delegate = delegate;
        this.messageListenable = messageListenable;
        this.serviceIdxListener = new ServiceIdxListener();
        this.instanceListener = new InstanceListener();
    }

    @Override
    public CompletableFuture<Set<String>> getServices(String namespace) {
        return namespaceMapServices.computeIfAbsent(namespace, (_namespace) -> {
            var serviceIdxKey = DiscoveryKeyGenerator.getServiceIdxKey(namespace);
            var serviceIdxTopic = ChannelTopic.of(serviceIdxKey);
            return messageListenable.addListener(serviceIdxTopic, serviceIdxListener)
                    .thenCompose(nil -> delegate.getServices(namespace));
        });
    }

    @Override
    public CompletableFuture<Set<String>> getServices() {
        return getServices(NamespacedContext.GLOBAL.getNamespace());
    }

    @Override
    public CompletableFuture<List<ServiceInstance>> getInstances(String serviceId) {
        return getInstances(NamespacedContext.GLOBAL.getNamespace(), serviceId);
    }

    @Override
    public CompletableFuture<List<ServiceInstance>> getInstances(String namespace, String serviceId) {
        return serviceMapInstances.computeIfAbsent(NamespacedServiceId.of(namespace, serviceId), (_serviceId) ->
                addListener(namespace, serviceId).
                        thenCompose(nil -> delegate.getInstances(serviceId))
        )
                .thenApply(serviceInstances -> serviceInstances.stream().filter(instance -> !instance.isExpired())
                        .collect(Collectors.toList()));
    }

    @VisibleForTesting
    public CompletableFuture<Void> addListener(String namespace, String serviceId) {
        PatternTopic instanceTopic = getPatternTopic(namespace, serviceId);
        return messageListenable.addListener(instanceTopic, instanceListener);
    }

    @VisibleForTesting
    public Future<Void> removeListener(String namespace, String serviceId) {
        PatternTopic instanceTopic = getPatternTopic(namespace, serviceId);
        return messageListenable.removeListener(instanceTopic, instanceListener);
    }

    private PatternTopic getPatternTopic(String namespace, String serviceId) {
        var instancePattern = DiscoveryKeyGenerator.getInstanceKeyPatternOfService(namespace, serviceId);
        var instanceTopic = PatternTopic.of(instancePattern);
        return instanceTopic;
    }


    private class ServiceIdxListener implements MessageListener {

        @Override
        public void onMessage(Topic topic, String channel, String message) {
            if (log.isInfoEnabled()) {
                log.info("onMessage@ServiceIdxListener - topic:[{}] - channel:[{}] - message:[{}]", topic, channel, message);
            }
            var serviceIdxKey = channel;
            var namespace = DiscoveryKeyGenerator.getNamespaceOfKey(serviceIdxKey);
            namespaceMapServices.put(namespace, delegate.getServices(namespace));
        }
    }

    private class InstanceListener implements MessageListener {

        @Override
        public void onMessage(Topic topic, String channel, String message) {
            if (log.isInfoEnabled()) {
                log.info("onMessage@InstanceListener - topic:[{}] - channel:[{}] - message:[{}]", topic, channel, message);
            }

            final var instanceKey = channel;
            var namespace = DiscoveryKeyGenerator.getNamespaceOfKey(instanceKey);
            var instanceId = DiscoveryKeyGenerator.getInstanceIdOfKey(namespace, instanceKey);
            var instance = InstanceIdGenerator.DEFAULT.of(instanceId);
            var serviceId = instance.getServiceId();
            /**
             * TODO 目前使用的是相应ServiceId的实例整体替换，按照 message :{@link ServiceEventType} 分解,单个实例替换
             */
            serviceMapInstances.put(NamespacedServiceId.of(namespace, serviceId), delegate.getInstances(namespace, serviceId));
        }
    }
}
