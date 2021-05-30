package me.ahoo.cosky.discovery.redis;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.cosky.discovery.*;
import me.ahoo.cosky.core.NamespacedContext;
import me.ahoo.cosky.core.listener.*;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * @author ahoo wang
 */
@Slf4j
public class ConsistencyRedisServiceDiscovery implements ServiceDiscovery, ServiceListenable {

    private final ServiceDiscovery delegate;
    private final MessageListenable messageListenable;
    private final ServiceIdxListener serviceIdxListener;
    private final InstanceListener instanceListener;

    private final ConcurrentHashMap<NamespacedServiceId, CompletableFuture<CopyOnWriteArrayList<ServiceInstance>>> serviceMapInstances;
    private final ConcurrentHashMap<NamespacedServiceId, CopyOnWriteArraySet<ServiceChangedListener>> serviceMapListener;
    private final ConcurrentHashMap<String, CompletableFuture<Set<String>>> namespaceMapServices;

    public ConsistencyRedisServiceDiscovery(ServiceDiscovery delegate, MessageListenable messageListenable) {
        this.serviceMapInstances = new ConcurrentHashMap<>();
        this.namespaceMapServices = new ConcurrentHashMap<>();
        this.serviceMapListener = new ConcurrentHashMap<>();
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
                        thenCompose(nil -> delegate.getInstances(namespace, serviceId)
                                .thenApply(serviceInstances -> new CopyOnWriteArrayList<ServiceInstance>(serviceInstances)))
        )
                .thenApply(serviceInstances -> serviceInstances.stream().filter(instance -> !instance.isExpired())
                        .collect(Collectors.toList()));
    }

    public CompletableFuture<ServiceInstance> getInstance0(String namespace, String serviceId, String instanceId) {
        var namespacedServiceId = NamespacedServiceId.of(namespace, serviceId);

        var instancesFuture = serviceMapInstances.get(namespacedServiceId);

        if (Objects.isNull(instancesFuture)) {
            return CompletableFuture.completedFuture(null);
        }

        return instancesFuture.thenApply(serviceInstances -> {
            if (Objects.isNull(serviceInstances)) {
                return null;
            }
            var cachedInstanceOp = serviceInstances.stream().filter(itc -> itc.getInstanceId().equals(instanceId)).findFirst();

            return cachedInstanceOp.orElse(ServiceInstance.NOT_FOUND);
        });
    }

    @Override
    public CompletableFuture<ServiceInstance> getInstance(String namespace, String serviceId, String instanceId) {
        return getInstance0(namespace, serviceId, instanceId).thenCompose(instance -> {
            if (ServiceInstance.NOT_FOUND.equals(instance)) {
                return CompletableFuture.completedFuture(null);
            }
            if (Objects.isNull(instance)) {
                return delegate.getInstance(namespace, serviceId, instanceId);
            }
            return CompletableFuture.completedFuture(instance);
        });
    }

    @Override
    public CompletableFuture<Long> getInstanceTtl(String namespace, String serviceId, String instanceId) {
        return getInstance0(namespace, serviceId, instanceId).thenCompose(instance -> {
            if (ServiceInstance.NOT_FOUND.equals(instance)) {
                return CompletableFuture.completedFuture(null);
            }
            if (Objects.isNull(instance)) {
                return delegate.getInstanceTtl(namespace, serviceId, instanceId);
            }
            return CompletableFuture.completedFuture(instance.getTtlAt());
        });
    }

    @VisibleForTesting
    public CompletableFuture<Void> addListener(String namespace, String serviceId) {
        PatternTopic instanceTopic = getPatternTopic(namespace, serviceId);
        return messageListenable.addListener(instanceTopic, instanceListener);
    }

    @Override
    public void addListener(NamespacedServiceId namespacedServiceId, ServiceChangedListener serviceChangedListener) {
        serviceMapListener.compute(namespacedServiceId, (key, val) -> {
            CopyOnWriteArraySet<ServiceChangedListener> listeners = val;
            if (Objects.isNull(val)) {
                listeners = new CopyOnWriteArraySet<>();
            }
            listeners.add(serviceChangedListener);
            return listeners;
        });
    }

    @Override
    public void removeListener(NamespacedServiceId namespacedServiceId, ServiceChangedListener serviceChangedListener) {
        serviceMapListener.compute(namespacedServiceId, (key, val) -> {
            if (Objects.isNull(val)) {
                return null;
            }
            CopyOnWriteArraySet<ServiceChangedListener> listeners = val;
            listeners.remove(serviceChangedListener);
            return listeners;
        });
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

            var namespacedServiceId = NamespacedServiceId.of(namespace, serviceId);
            var serviceChangedListeners = serviceMapListener.get(namespacedServiceId);

            var instancesFuture = serviceMapInstances.get(namespacedServiceId);

            if (Objects.isNull(instancesFuture)) {
                if (log.isInfoEnabled()) {
                    log.info("onMessage@InstanceListener - topic:[{}] - channel:[{}] - message:[{}] instancesFuture is null.", topic, channel, message);
                }
                invokeChanged(message, namespacedServiceId, serviceChangedListeners);
                return;
            }

            instancesFuture.thenCompose(cachedInstances -> {
                if (Objects.isNull(cachedInstances)) {
                    if (log.isInfoEnabled()) {
                        log.info("onMessage@InstanceListener - topic:[{}] - channel:[{}] - message:[{}] cachedInstances is null.", topic, channel, message);
                    }
                    return CompletableFuture.completedFuture(null);
                }

                var cachedInstance = cachedInstances.stream()
                        .filter(itc -> itc.getInstanceId().equals(instanceId))
                        .findFirst().orElse(ServiceInstance.NOT_FOUND);

                if (ServiceChangedListener.REGISTER.equals(message)) {
                    if (log.isInfoEnabled()) {
                        log.info("onMessage@InstanceListener - topic:[{}] - channel:[{}] - message:[{}] add registered Instance.", topic, channel, message);
                    }
                    return delegate.getInstance(namespace, serviceId, instanceId).thenAccept(registeredInstance -> {
                        if (ServiceInstance.NOT_FOUND.equals(cachedInstance)) {
                            cachedInstances.add(registeredInstance);
                        } else {
                            cachedInstance.setSchema(registeredInstance.getSchema());
                            cachedInstance.setHost(registeredInstance.getHost());
                            cachedInstance.setPort(registeredInstance.getPort());
                            cachedInstance.setEphemeral(registeredInstance.isEphemeral());
                            cachedInstance.setTtlAt(registeredInstance.getTtlAt());
                            cachedInstance.setWeight(registeredInstance.getWeight());
                            cachedInstance.setMetadata(registeredInstance.getMetadata());
                        }
                    });
                }


                if (ServiceInstance.NOT_FOUND.equals(cachedInstance)) {
                    if (log.isWarnEnabled()) {
                        log.warn("onMessage@InstanceListener - topic:[{}] - channel:[{}] - message:[{}] not found cached Instance.", topic, channel, message);
                    }
                    return CompletableFuture.completedFuture(null);
                }

                switch (message) {
                    case ServiceChangedListener.RENEW: {
                        if (log.isInfoEnabled()) {
                            log.info("onMessage@InstanceListener - topic:[{}] - channel:[{}] - message:[{}] setTtlAt.", topic, channel, message);
                        }
                        return delegate.getInstanceTtl(namespace, serviceId, instanceId).thenAccept(nextTtlAt -> cachedInstance.setTtlAt(nextTtlAt));
                    }
                    case ServiceChangedListener.SET_METADATA: {
                        if (log.isInfoEnabled()) {
                            log.info("onMessage@InstanceListener - topic:[{}] - channel:[{}] - message:[{}] setMetadata.", topic, channel, message);
                        }
                        return delegate.getInstance(namespace, serviceId, instanceId).thenAccept(nextInstance -> cachedInstance.setMetadata(nextInstance.getMetadata()));
                    }
                    case ServiceChangedListener.DEREGISTER:
                    case ServiceChangedListener.EXPIRED: {
                        if (log.isInfoEnabled()) {
                            log.info("onMessage@InstanceListener - topic:[{}] - channel:[{}] - message:[{}] remove instance.", topic, channel, message);
                        }
                        cachedInstances.remove(cachedInstance);
                        return CompletableFuture.completedFuture(null);
                    }
                    default:
                        throw new IllegalStateException("Unexpected value: " + message);
                }

            }).thenAccept(nil -> invokeChanged(message, namespacedServiceId, serviceChangedListeners));
        }

        private void invokeChanged(String message, NamespacedServiceId namespacedServiceId, CopyOnWriteArraySet<ServiceChangedListener> serviceChangedListeners) {
            if (Objects.nonNull(serviceChangedListeners) && !serviceChangedListeners.isEmpty()) {
                serviceChangedListeners.forEach(serviceChangedListener -> serviceChangedListener.onChange(namespacedServiceId, message));
            }
        }
    }
}
