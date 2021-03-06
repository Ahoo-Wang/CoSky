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
import me.ahoo.cosky.discovery.ListenableServiceDiscovery;
import me.ahoo.cosky.discovery.NamespacedServiceId;
import me.ahoo.cosky.discovery.ServiceChangedEvent;
import me.ahoo.cosky.discovery.ServiceDiscovery;
import me.ahoo.cosky.discovery.ServiceInstance;
import me.ahoo.cosky.discovery.ServiceInstanceContext;
import me.ahoo.cosky.discovery.ServiceTopology;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Consistency Redis Service Discovery.
 *
 * @author ahoo wang
 */
@Slf4j
public class ConsistencyRedisServiceDiscovery implements ListenableServiceDiscovery, ServiceTopology {
    
    private final ServiceDiscovery delegate;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ReactiveRedisMessageListenerContainer listenerContainer;
    
    private final ConcurrentHashMap<NamespacedServiceId, Mono<CopyOnWriteArraySet<ServiceInstance>>> serviceMapInstances;
    private final ConcurrentHashMap<String, Flux<String>> namespaceMapServices;
    
    @VisibleForTesting
    @Nullable
    private final Consumer<ServiceChangedEvent> hookOnResetInstanceCache;
    
    @VisibleForTesting
    @Nullable
    private final Consumer<String> hookOnResetServiceCache;
    
    public ConsistencyRedisServiceDiscovery(ServiceDiscovery delegate,
                                            ReactiveStringRedisTemplate redisTemplate,
                                            ReactiveRedisMessageListenerContainer listenerContainer) {
        this(delegate, redisTemplate, listenerContainer, null, null);
    }
    
    public ConsistencyRedisServiceDiscovery(ServiceDiscovery delegate,
                                            ReactiveStringRedisTemplate redisTemplate,
                                            ReactiveRedisMessageListenerContainer listenerContainer,
                                            Consumer<ServiceChangedEvent> hookOnResetInstanceCache,
                                            Consumer<String> hookOnResetServiceCache) {
        this.redisTemplate = redisTemplate;
        this.serviceMapInstances = new ConcurrentHashMap<>();
        this.namespaceMapServices = new ConcurrentHashMap<>();
        this.delegate = delegate;
        this.listenerContainer = listenerContainer;
        this.hookOnResetInstanceCache = hookOnResetInstanceCache;
        this.hookOnResetServiceCache = hookOnResetServiceCache;
    }
    
    @Override
    public Flux<ServiceChangedEvent> listen(NamespacedServiceId topic) {
        String instancePattern = DiscoveryKeyGenerator.getInstanceKeyPatternOfService(topic.getNamespace(), topic.getServiceId());
        return listenerContainer.receive(PatternTopic.of(instancePattern))
            .map(message -> {
                String namespace = DiscoveryKeyGenerator.getNamespaceOfKey(message.getChannel());
                String instanceId = DiscoveryKeyGenerator.getInstanceIdOfKey(namespace, message.getChannel());
                Instance instance = InstanceIdGenerator.DEFAULT.of(instanceId);
                String serviceId = instance.getServiceId();
                NamespacedServiceId namespacedServiceId = NamespacedServiceId.of(namespace, serviceId);
                return new ServiceChangedEvent(namespacedServiceId, ServiceChangedEvent.Event.of(message.getMessage()), instance);
            })
            .doOnComplete(() -> {
                addTopology(topic.getNamespace(), topic.getServiceId()).subscribe();
            });
    }
    
    @Override
    public Flux<String> getServices(String namespace) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        return namespaceMapServices.computeIfAbsent(namespace, (np) -> listenServiceAndGetCache(namespace));
    }
    
    private Flux<String> listenServiceAndGetCache(String namespace) {
        String serviceIdxKey = DiscoveryKeyGenerator.getServiceIdxKey(namespace);
        listenerContainer.receive(ChannelTopic.of(serviceIdxKey))
            .subscribe(new ServiceChangedSubscriber(this));
        return delegate.getServices(namespace).cache();
    }
    
    private void onServiceChanged(ReactiveSubscription.Message<String, String> message) {
        if (log.isInfoEnabled()) {
            log.info("onServiceChanged - channel:[{}] - message:[{}]", message.getChannel(), message.getMessage());
        }
        String namespace = DiscoveryKeyGenerator.getNamespaceOfKey(message.getChannel());
        namespaceMapServices.put(namespace, delegate.getServices(namespace).cache());
        if (null != hookOnResetServiceCache) {
            hookOnResetServiceCache.accept(namespace);
        }
    }
    
    @Override
    public Flux<ServiceInstance> getInstances(String namespace, String serviceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceId), "serviceId can not be empty!");
        
        return serviceMapInstances.computeIfAbsent(NamespacedServiceId.of(namespace, serviceId),
                (svcId) -> {
                    listen(svcId).subscribe(new InstanceChangedEventSubscriber(this));
                    return delegate.getInstances(namespace, serviceId)
                        .collectList()
                        .map(CopyOnWriteArraySet::new)
                        .cache();
                }
            )
            .flatMapIterable(Function.identity())
            .filter(instance -> !instance.isExpired());
    }
    
    public Mono<ServiceInstance> getInstance0(String namespace, String serviceId, String instanceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceId), "serviceId can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(instanceId), "instanceId can not be empty!");
        
        NamespacedServiceId namespacedServiceId = NamespacedServiceId.of(namespace, serviceId);
        
        Mono<CopyOnWriteArraySet<ServiceInstance>> instancesMono = serviceMapInstances.get(namespacedServiceId);
        
        if (Objects.isNull(instancesMono)) {
            return delegate.getInstance(namespace, serviceId, instanceId);
        }
        
        return instancesMono
            .flatMapIterable(Function.identity())
            .switchIfEmpty(delegate.getInstance(namespace, serviceId, instanceId))
            .filter(itc -> itc.getInstanceId().equals(instanceId))
            .next();
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
    
    @Override
    public Mono<Void> addTopology(String producerNamespace, String producerServiceId) {
        final String consumerNamespace = ServiceInstanceContext.CURRENT.getNamespace();
        final String consumerName = ServiceTopology.getConsumerName();
        final String producerName = ServiceTopology.getProducerName(producerNamespace, producerServiceId);
        if (Objects.equals(consumerName, producerName)) {
            return Mono.empty();
        }
        
        return redisTemplate.execute(
                DiscoveryRedisScripts.SCRIPT_TOPOLOGY_ADD,
                Collections.singletonList(consumerNamespace),
                Arrays.asList(consumerName, producerName)
            )
            .then();
    }
    
    private void onInstanceChanged(ServiceChangedEvent serviceChangedEvent) {
        if (log.isInfoEnabled()) {
            log.info("onInstanceChanged - instance:[{}] - message:[{}]", serviceChangedEvent.getInstance(), serviceChangedEvent.getEvent());
        }
        
        NamespacedServiceId namespacedServiceId = serviceChangedEvent.getNamespacedServiceId();
        Instance instance = serviceChangedEvent.getInstance();
        String instanceId = instance.getInstanceId();
        String namespace = namespacedServiceId.getNamespace();
        String serviceId = namespacedServiceId.getServiceId();
        
        Mono<CopyOnWriteArraySet<ServiceInstance>> instancesMono = serviceMapInstances.get(namespacedServiceId);
        
        if (Objects.isNull(instancesMono)) {
            if (log.isInfoEnabled()) {
                log.info("onInstanceChanged - instance:[{}] - event:[{}] instancesMono is null.", instance, serviceChangedEvent.getEvent());
            }
            return;
        }
        
        instancesMono.flatMap(cachedInstances -> {
            ServiceInstance cachedInstance = cachedInstances.stream()
                .filter(itc -> itc.getInstanceId().equals(instanceId))
                .findFirst()
                .orElse(ServiceInstance.NOT_FOUND);
            
            if (ServiceInstance.NOT_FOUND.equals(cachedInstance)) {
                if (!ServiceChangedEvent.Event.REGISTER.equals(serviceChangedEvent.getEvent())
                    && !ServiceChangedEvent.Event.RENEW.equals(serviceChangedEvent.getEvent())
                ) {
                    if (log.isWarnEnabled()) {
                        log.warn("onInstanceChanged - instance:[{}] - event:[{}] not found cached Instance.", instance, serviceChangedEvent.getEvent());
                    }
                    return Mono.empty();
                }
                return delegate.getInstance(namespace, serviceId, instanceId).doOnNext(serviceInstance -> {
                    if (log.isInfoEnabled()) {
                        log.info("onInstanceChanged - instance:[{}] - event:[{}] add registered Instance.", instance, serviceChangedEvent.getEvent());
                    }
                    cachedInstances.add(serviceInstance);
                });
            }
            
            switch (serviceChangedEvent.getEvent()) {
                case REGISTER: {
                    return delegate
                        .getInstance(namespace, serviceId, instanceId)
                        .doOnNext(registeredInstance -> {
                            cachedInstance.setSchema(registeredInstance.getSchema());
                            cachedInstance.setHost(registeredInstance.getHost());
                            cachedInstance.setPort(registeredInstance.getPort());
                            cachedInstance.setEphemeral(registeredInstance.isEphemeral());
                            cachedInstance.setTtlAt(registeredInstance.getTtlAt());
                            cachedInstance.setWeight(registeredInstance.getWeight());
                            cachedInstance.setMetadata(registeredInstance.getMetadata());
                        });
                }
                case RENEW: {
                    if (log.isInfoEnabled()) {
                        log.info("onInstanceChanged - instance:[{}] - event:[{}] setTtlAt.", instance, serviceChangedEvent.getEvent());
                    }
                    return delegate
                        .getInstanceTtl(namespace, serviceId, instanceId)
                        .doOnNext(cachedInstance::setTtlAt);
                }
                case SET_METADATA: {
                    if (log.isInfoEnabled()) {
                        log.info("onInstanceChanged - instance:[{}] - event:[{}] setMetadata.", instance, serviceChangedEvent.getEvent());
                    }
                    return delegate
                        .getInstance(namespace, serviceId, instanceId)
                        .doOnNext(nextInstance -> cachedInstance.setMetadata(nextInstance.getMetadata()));
                }
                case DEREGISTER:
                case EXPIRED: {
                    if (log.isInfoEnabled()) {
                        log.info("onInstanceChanged - instance:[{}] - event:[{}] remove instance.", instance, serviceChangedEvent.getEvent());
                    }
                    cachedInstances.remove(cachedInstance);
                    return Mono.empty();
                }
                default:
                    return Mono.error(new IllegalStateException("Unexpected value: " + serviceChangedEvent.getEvent()));
            }
        }).doOnSuccess(nil -> {
            if (null != hookOnResetInstanceCache) {
                hookOnResetInstanceCache.accept(serviceChangedEvent);
            }
        }).subscribe();
    }
    
    private static class InstanceChangedEventSubscriber extends BaseSubscriber<ServiceChangedEvent> {
        private final ConsistencyRedisServiceDiscovery serviceDiscovery;
        
        public InstanceChangedEventSubscriber(ConsistencyRedisServiceDiscovery serviceDiscovery) {
            this.serviceDiscovery = serviceDiscovery;
        }
        
        @Override
        protected void hookOnNext(ServiceChangedEvent value) {
            serviceDiscovery.onInstanceChanged(value);
        }
        
        @Override
        protected void hookOnError(Throwable throwable) {
            if (log.isErrorEnabled()) {
                log.error("hookOnError - " + throwable.getMessage(), throwable);
            }
        }
    }
    
    private static class ServiceChangedSubscriber extends BaseSubscriber<ReactiveSubscription.Message<String, String>> {
        private final ConsistencyRedisServiceDiscovery serviceDiscovery;
        
        public ServiceChangedSubscriber(ConsistencyRedisServiceDiscovery serviceDiscovery) {
            this.serviceDiscovery = serviceDiscovery;
        }
        
        @Override
        protected void hookOnNext(ReactiveSubscription.Message<String, String> message) {
            serviceDiscovery.onServiceChanged(message);
        }
        
        @Override
        protected void hookOnError(Throwable throwable) {
            if (log.isErrorEnabled()) {
                log.error("hookOnError - " + throwable.getMessage(), throwable);
            }
        }
    }
    
}
