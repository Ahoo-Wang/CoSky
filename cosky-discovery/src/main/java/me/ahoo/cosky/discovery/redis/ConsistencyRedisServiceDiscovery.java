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
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosky.core.listener.*;
import me.ahoo.cosky.discovery.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author ahoo wang
 */
@Slf4j
public class ConsistencyRedisServiceDiscovery implements ServiceDiscovery, ServiceListenable, ServiceTopology {

    private final ServiceDiscovery delegate;
    private final MessageListenable messageListenable;
    private final RedisClusterReactiveCommands<String, String> redisCommands;
    private final ServiceIdxListener serviceIdxListener;
    private final InstanceListener instanceListener;

    private final ConcurrentHashMap<NamespacedServiceId, Mono<CopyOnWriteArrayList<ServiceInstance>>> serviceMapInstances;
    private final ConcurrentHashMap<NamespacedServiceId, CopyOnWriteArraySet<ServiceChangedListener>> serviceMapListener;
    private final ConcurrentHashMap<String, Mono<List<String>>> namespaceMapServices;

    public ConsistencyRedisServiceDiscovery(ServiceDiscovery delegate
            , MessageListenable messageListenable
            , RedisClusterReactiveCommands<String, String> redisCommands) {
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
    public Mono<List<String>> getServices(String namespace) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        return namespaceMapServices.computeIfAbsent(namespace, (_namespace) -> {
            String serviceIdxKey = DiscoveryKeyGenerator.getServiceIdxKey(namespace);
            messageListenable.addChannelListener(serviceIdxKey, serviceIdxListener);
            return delegate.getServices(namespace).cache();
        });
    }

    @Override
    public Mono<List<ServiceInstance>> getInstances(String namespace, String serviceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceId), "serviceId can not be empty!");

        return serviceMapInstances.computeIfAbsent(NamespacedServiceId.of(namespace, serviceId), (_serviceId) ->
                        addListener(namespace, serviceId).
                                then(delegate.getInstances(namespace, serviceId))
                                .map(CopyOnWriteArrayList::new)
                                .cache()
                )
                .map(serviceInstances -> serviceInstances.stream()
                        .filter(instance -> !instance.isExpired())
                        .collect(Collectors.toList()));
    }

    public Mono<ServiceInstance> getInstance0(String namespace, String serviceId, String instanceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceId), "serviceId can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(instanceId), "instanceId can not be empty!");

        NamespacedServiceId namespacedServiceId = NamespacedServiceId.of(namespace, serviceId);

        Mono<CopyOnWriteArrayList<ServiceInstance>> instancesMono = serviceMapInstances.get(namespacedServiceId);

        if (Objects.isNull(instancesMono)) {
            return delegate.getInstance(namespace, serviceId, instanceId);
        }

        return instancesMono.mapNotNull(serviceInstances -> serviceInstances
                .stream()
                .filter(itc -> itc.getInstanceId().equals(instanceId))
                .findFirst()
                .orElse(null)
        );
    }

    @Override
    public Mono<ServiceInstance> getInstance(String namespace, String serviceId, String instanceId) {
        return getInstance0(namespace, serviceId, instanceId);
    }

    @Override
    public Mono<Long> getInstanceTtl(String namespace, String serviceId, String instanceId) {
        return getInstance0(namespace, serviceId, instanceId)
                .map(ServiceInstance::getTtlAt);
    }

    @VisibleForTesting
    public Mono<Void> addListener(String namespace, String serviceId) {
        String instancePattern = DiscoveryKeyGenerator.getInstanceKeyPatternOfService(namespace, serviceId);
        messageListenable.addPatternListener(instancePattern, instanceListener);
        return addTopology(namespace, serviceId);
    }

    @Override
    public Mono<Void> addTopology(String producerNamespace, String producerServiceId) {
        final String consumerNamespace = ServiceInstanceContext.CURRENT.getNamespace();
        final String consumerName = ServiceTopology.getConsumerName();
        final String producerName = ServiceTopology.getProducerName(producerNamespace, producerServiceId);
        if (Objects.equals(consumerName, producerName)) {
            return Mono.empty();
        }
        return DiscoveryRedisScripts.doServiceTopologyAdd(redisCommands, (sha) -> {
            String[] keys = {consumerNamespace};
            String[] values = {consumerName, producerName};
            return redisCommands.evalsha(sha, ScriptOutputType.STATUS, keys, values).then();
        });
    }

    @Override
    public void addListener(NamespacedServiceId namespacedServiceId, ServiceChangedListener serviceChangedListener) {
        serviceMapListener.compute(namespacedServiceId, (key, val) -> {
            CopyOnWriteArraySet<ServiceChangedListener> listeners = val;
            if (Objects.isNull(val)) {
                addListener(namespacedServiceId.getNamespace(), namespacedServiceId.getServiceId()).subscribe();
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
    public void removeListener(String namespace, String serviceId) {
        String instancePattern = DiscoveryKeyGenerator.getInstanceKeyPatternOfService(namespace, serviceId);
        messageListenable.removePatternListener(instancePattern, instanceListener);
    }

    private class ServiceIdxListener implements MessageListener {

        @Override
        public void onMessage(@Nullable String pattern, String channel, String message) {
            if (log.isInfoEnabled()) {
                log.info("onMessage@ServiceIdxListener - pattern:[{}] - channel:[{}] - message:[{}]", pattern, channel, message);
            }
            String serviceIdxKey = channel;
            String namespace = DiscoveryKeyGenerator.getNamespaceOfKey(serviceIdxKey);
            namespaceMapServices.put(namespace, delegate.getServices(namespace).cache());
        }
    }

    private class InstanceListener implements MessageListener {


        @Override
        public void onMessage(@Nullable String pattern, String channel, String message) {
            if (log.isInfoEnabled()) {
                log.info("onMessage@InstanceListener - pattern:[{}] - channel:[{}] - message:[{}]", pattern, channel, message);
            }

            final String instanceKey = channel;
            String namespace = DiscoveryKeyGenerator.getNamespaceOfKey(instanceKey);
            String instanceId = DiscoveryKeyGenerator.getInstanceIdOfKey(namespace, instanceKey);
            Instance instance = InstanceIdGenerator.DEFAULT.of(instanceId);
            String serviceId = instance.getServiceId();

            NamespacedServiceId namespacedServiceId = NamespacedServiceId.of(namespace, serviceId);
            AtomicReference<ServiceChangedEvent> serviceChangedEvent = new AtomicReference<>(ServiceChangedEvent.of(namespacedServiceId, message, instance));
            CopyOnWriteArraySet<ServiceChangedListener> serviceChangedListeners = serviceMapListener.get(namespacedServiceId);

            Mono<CopyOnWriteArrayList<ServiceInstance>> instancesMono = serviceMapInstances.get(namespacedServiceId);

            if (Objects.isNull(instancesMono)) {
                if (log.isInfoEnabled()) {
                    log.info("onMessage@InstanceListener - pattern:[{}] - channel:[{}] - message:[{}] instancesMono is null.", pattern, channel, message);
                }
                invokeChanged(serviceChangedEvent.get(), serviceChangedListeners);
                return;
            }

            instancesMono.flatMap(cachedInstances -> {
                ServiceInstance cachedInstance = cachedInstances.stream()
                        .filter(itc -> itc.getInstanceId().equals(instanceId))
                        .findFirst().orElse(ServiceInstance.NOT_FOUND);

                if (ServiceInstance.NOT_FOUND.equals(cachedInstance)) {
                    if (!ServiceChangedEvent.REGISTER.equals(message) && !ServiceChangedEvent.RENEW.equals(message)) {
                        if (log.isWarnEnabled()) {
                            log.warn("onMessage@InstanceListener - pattern:[{}] - channel:[{}] - message:[{}] not found cached Instance.", pattern, channel, message);
                        }
                        return Mono.empty();
                    }
                    return delegate.getInstance(namespace, serviceId, instanceId).doOnNext(serviceInstance -> {
                        if (log.isInfoEnabled()) {
                            log.info("onMessage@InstanceListener - pattern:[{}] - channel:[{}] - message:[{}] add registered Instance.", pattern, channel, message);
                        }
                        cachedInstances.add(serviceInstance);
                    });
                }

                switch (message) {
                    case ServiceChangedEvent.REGISTER: {
                        return delegate.getInstance(namespace, serviceId, instanceId).doOnNext(registeredInstance -> {
                            cachedInstance.setSchema(registeredInstance.getSchema());
                            cachedInstance.setHost(registeredInstance.getHost());
                            cachedInstance.setPort(registeredInstance.getPort());
                            cachedInstance.setEphemeral(registeredInstance.isEphemeral());
                            cachedInstance.setTtlAt(registeredInstance.getTtlAt());
                            cachedInstance.setWeight(registeredInstance.getWeight());
                            cachedInstance.setMetadata(registeredInstance.getMetadata());
                        });
                    }
                    case ServiceChangedEvent.RENEW: {
                        if (log.isInfoEnabled()) {
                            log.info("onMessage@InstanceListener - pattern:[{}] - channel:[{}] - message:[{}] setTtlAt.", pattern, channel, message);
                        }
                        return delegate.getInstanceTtl(namespace, serviceId, instanceId).doOnNext(cachedInstance::setTtlAt);
                    }
                    case ServiceChangedEvent.SET_METADATA: {
                        if (log.isInfoEnabled()) {
                            log.info("onMessage@InstanceListener - pattern:[{}] - channel:[{}] - message:[{}] setMetadata.", pattern, channel, message);
                        }
                        return delegate.getInstance(namespace, serviceId, instanceId).doOnNext(nextInstance -> cachedInstance.setMetadata(nextInstance.getMetadata()));
                    }
                    case ServiceChangedEvent.DEREGISTER:
                    case ServiceChangedEvent.EXPIRED: {
                        if (log.isInfoEnabled()) {
                            log.info("onMessage@InstanceListener - pattern:[{}] - channel:[{}] - message:[{}] remove instance.", pattern, channel, message);
                        }
                        serviceChangedEvent.set(ServiceChangedEvent.of(namespacedServiceId, message, cachedInstance));
                        cachedInstances.remove(cachedInstance);
                        return Mono.empty();
                    }
                    default:
                        return Mono.error(new IllegalStateException("Unexpected value: " + message));
                }
            }).doOnSuccess(nil -> invokeChanged(serviceChangedEvent.get(), serviceChangedListeners)).subscribe();
        }

        private void invokeChanged(ServiceChangedEvent serviceChangedEvent, CopyOnWriteArraySet<ServiceChangedListener> serviceChangedListeners) {
            if (Objects.nonNull(serviceChangedListeners) && !serviceChangedListeners.isEmpty()) {
                serviceChangedListeners.forEach(serviceChangedListener -> serviceChangedListener.onChange(serviceChangedEvent));
            }
        }
    }
}
