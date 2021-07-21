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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosky.discovery.*;
import me.ahoo.cosky.core.NamespacedContext;
import me.ahoo.cosky.core.listener.*;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author ahoo wang
 */
@Slf4j
public class ConsistencyRedisServiceDiscovery implements ServiceDiscovery, ServiceListenable, ServiceTopology {

    private final ServiceDiscovery delegate;
    private final MessageListenable messageListenable;
    private final RedisClusterAsyncCommands<String, String> redisCommands;
    private final ServiceIdxListener serviceIdxListener;
    private final InstanceListener instanceListener;

    private final ConcurrentHashMap<NamespacedServiceId, CompletableFuture<CopyOnWriteArrayList<ServiceInstance>>> serviceMapInstances;
    private final ConcurrentHashMap<NamespacedServiceId, CopyOnWriteArraySet<ServiceChangedListener>> serviceMapListener;
    private final ConcurrentHashMap<String, CompletableFuture<Set<String>>> namespaceMapServices;

    public ConsistencyRedisServiceDiscovery(ServiceDiscovery delegate, MessageListenable messageListenable, RedisClusterAsyncCommands<String, String> redisCommands) {
        this.redisCommands = redisCommands;
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
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");

        return namespaceMapServices.computeIfAbsent(namespace, (_namespace) -> {
            String serviceIdxKey = DiscoveryKeyGenerator.getServiceIdxKey(namespace);
            ChannelTopic serviceIdxTopic = ChannelTopic.of(serviceIdxKey);
            return messageListenable.addListener(serviceIdxTopic, serviceIdxListener)
                    .thenCompose(nil -> delegate.getServices(namespace));
        });
    }

    @Override
    public CompletableFuture<Set<String>> getServices() {
        return getServices(NamespacedContext.GLOBAL.getRequiredNamespace());
    }

    @Override
    public CompletableFuture<List<ServiceInstance>> getInstances(String serviceId) {
        return getInstances(NamespacedContext.GLOBAL.getRequiredNamespace(), serviceId);
    }

    @Override
    public CompletableFuture<List<ServiceInstance>> getInstances(String namespace, String serviceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceId), "serviceId can not be empty!");

        return serviceMapInstances.computeIfAbsent(NamespacedServiceId.of(namespace, serviceId), (_serviceId) ->
                addListener(namespace, serviceId).
                        thenCompose(nil -> delegate.getInstances(namespace, serviceId)
                                .thenApply(serviceInstances -> new CopyOnWriteArrayList<>(serviceInstances)))
        )
                .thenApply(serviceInstances -> serviceInstances.stream().filter(instance -> !instance.isExpired())
                        .collect(Collectors.toList()));
    }

    public CompletableFuture<ServiceInstance> getInstance0(String namespace, String serviceId, String instanceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceId), "serviceId can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(instanceId), "instanceId can not be empty!");

        NamespacedServiceId namespacedServiceId = NamespacedServiceId.of(namespace, serviceId);

        CompletableFuture<CopyOnWriteArrayList<ServiceInstance>> instancesFuture = serviceMapInstances.get(namespacedServiceId);

        if (Objects.isNull(instancesFuture)) {
            return CompletableFuture.completedFuture(null);
        }

        return instancesFuture.thenApply(serviceInstances -> {
            if (Objects.isNull(serviceInstances)) {
                return null;
            }
            Optional<ServiceInstance> cachedInstanceOp = serviceInstances.stream().filter(itc -> itc.getInstanceId().equals(instanceId)).findFirst();

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
        return messageListenable.addListener(instanceTopic, instanceListener)
                .thenCompose(nil -> addTopology(namespace, serviceId));
    }

    @Override
    public CompletableFuture<Void> addTopology(String producerNamespace, String producerServiceId) {
        final String consumerNamespace = ServiceInstanceContext.CURRENT.getNamespace();
        final String consumerName = ServiceTopology.getConsumerName();
        final String producerName = ServiceTopology.getProducerName(producerNamespace, producerServiceId);
        if (Objects.equals(consumerName, producerName)) {
            return CompletableFuture.completedFuture(null);
        }
        return DiscoveryRedisScripts.doServiceTopologyAdd(redisCommands, (sha) -> {
            String[] keys = {consumerNamespace};
            String[] values = {consumerName, producerName};
            return redisCommands.evalsha(sha, ScriptOutputType.STATUS, keys, values);
        });
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
        String instancePattern = DiscoveryKeyGenerator.getInstanceKeyPatternOfService(namespace, serviceId);
        PatternTopic instanceTopic = PatternTopic.of(instancePattern);
        return instanceTopic;
    }


    private class ServiceIdxListener implements MessageListener {

        @Override
        public void onMessage(Topic topic, String channel, String message) {
            if (log.isInfoEnabled()) {
                log.info("onMessage@ServiceIdxListener - topic:[{}] - channel:[{}] - message:[{}]", topic, channel, message);
            }
            String serviceIdxKey = channel;
            String namespace = DiscoveryKeyGenerator.getNamespaceOfKey(serviceIdxKey);
            namespaceMapServices.put(namespace, delegate.getServices(namespace));
        }
    }

    private class InstanceListener implements MessageListener {


        @Override
        public void onMessage(Topic topic, String channel, String message) {
            if (log.isInfoEnabled()) {
                log.info("onMessage@InstanceListener - topic:[{}] - channel:[{}] - message:[{}]", topic, channel, message);
            }

            final String instanceKey = channel;
            String namespace = DiscoveryKeyGenerator.getNamespaceOfKey(instanceKey);
            String instanceId = DiscoveryKeyGenerator.getInstanceIdOfKey(namespace, instanceKey);
            Instance instance = InstanceIdGenerator.DEFAULT.of(instanceId);
            String serviceId = instance.getServiceId();

            NamespacedServiceId namespacedServiceId = NamespacedServiceId.of(namespace, serviceId);
            AtomicReference<ServiceChangedEvent> serviceChangedEvent = new AtomicReference<>(ServiceChangedEvent.of(namespacedServiceId, message, instance));
            CopyOnWriteArraySet<ServiceChangedListener> serviceChangedListeners = serviceMapListener.get(namespacedServiceId);

            CompletableFuture<CopyOnWriteArrayList<ServiceInstance>> instancesFuture = serviceMapInstances.get(namespacedServiceId);

            if (Objects.isNull(instancesFuture)) {
                if (log.isInfoEnabled()) {
                    log.info("onMessage@InstanceListener - topic:[{}] - channel:[{}] - message:[{}] instancesFuture is null.", topic, channel, message);
                }
                invokeChanged(serviceChangedEvent.get(), serviceChangedListeners);
                return;
            }

            instancesFuture.thenCompose(cachedInstances -> {
                if (Objects.isNull(cachedInstances)) {
                    if (log.isInfoEnabled()) {
                        log.info("onMessage@InstanceListener - topic:[{}] - channel:[{}] - message:[{}] cachedInstances is null.", topic, channel, message);
                    }
                    return CompletableFuture.completedFuture(null);
                }

                ServiceInstance cachedInstance = cachedInstances.stream()
                        .filter(itc -> itc.getInstanceId().equals(instanceId))
                        .findFirst().orElse(ServiceInstance.NOT_FOUND);

                if (ServiceChangedEvent.REGISTER.equals(message)) {
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
                    case ServiceChangedEvent.RENEW: {
                        if (log.isInfoEnabled()) {
                            log.info("onMessage@InstanceListener - topic:[{}] - channel:[{}] - message:[{}] setTtlAt.", topic, channel, message);
                        }
                        return delegate.getInstanceTtl(namespace, serviceId, instanceId).thenAccept(nextTtlAt -> cachedInstance.setTtlAt(nextTtlAt));
                    }
                    case ServiceChangedEvent.SET_METADATA: {
                        if (log.isInfoEnabled()) {
                            log.info("onMessage@InstanceListener - topic:[{}] - channel:[{}] - message:[{}] setMetadata.", topic, channel, message);
                        }
                        return delegate.getInstance(namespace, serviceId, instanceId).thenAccept(nextInstance -> cachedInstance.setMetadata(nextInstance.getMetadata()));
                    }
                    case ServiceChangedEvent.DEREGISTER:
                    case ServiceChangedEvent.EXPIRED: {
                        if (log.isInfoEnabled()) {
                            log.info("onMessage@InstanceListener - topic:[{}] - channel:[{}] - message:[{}] remove instance.", topic, channel, message);
                        }
                        serviceChangedEvent.set(ServiceChangedEvent.of(namespacedServiceId, message, cachedInstance));
                        cachedInstances.remove(cachedInstance);
                        return CompletableFuture.completedFuture(null);
                    }
                    default:
                        throw new IllegalStateException("Unexpected value: " + message);
                }

            }).thenAccept(nil -> invokeChanged(serviceChangedEvent.get(), serviceChangedListeners));
        }

        private void invokeChanged(ServiceChangedEvent serviceChangedEvent, CopyOnWriteArraySet<ServiceChangedListener> serviceChangedListeners) {
            if (Objects.nonNull(serviceChangedListeners) && !serviceChangedListeners.isEmpty()) {
                serviceChangedListeners.forEach(serviceChangedListener -> serviceChangedListener.onChange(serviceChangedEvent));
            }
        }
    }
}
