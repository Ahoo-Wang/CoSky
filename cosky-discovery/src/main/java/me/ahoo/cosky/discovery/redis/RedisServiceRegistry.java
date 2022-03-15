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

import me.ahoo.cosky.discovery.InstanceIdGenerator;
import me.ahoo.cosky.discovery.NamespacedInstanceId;
import me.ahoo.cosky.discovery.RegistryProperties;
import me.ahoo.cosky.discovery.ServiceInstance;
import me.ahoo.cosky.discovery.ServiceInstanceCodec;
import me.ahoo.cosky.discovery.ServiceRegistry;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis Service Registry.
 *
 * @author ahoo wang
 */
@Slf4j
public class RedisServiceRegistry implements ServiceRegistry {
    
    private final RegistryProperties registryProperties;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ConcurrentHashMap<NamespacedInstanceId, ServiceInstance> registeredEphemeralInstances;
    
    public RedisServiceRegistry(RegistryProperties registryProperties,
                                ReactiveStringRedisTemplate redisTemplate) {
        this.registeredEphemeralInstances = new ConcurrentHashMap<>();
        this.registryProperties = registryProperties;
        this.redisTemplate = redisTemplate;
    }
    
    private Mono<Boolean> register0(String namespace, ServiceInstance serviceInstance) {
        /**
         * ARGV
         */
        String[] infoArgs = {
            /**
             * local instanceTtl = ARGV[1];
            */
            serviceInstance.isEphemeral() ? String.valueOf(registryProperties.getInstanceTtl()) : "-1",
            /**
             * local serviceId = ARGV[2];
            */
            serviceInstance.getServiceId(),
            /**
             * local instanceId = ARGV[3];
            */
            serviceInstance.getInstanceId(),
            /**
             * local scheme = ARGV[4];
            */
            serviceInstance.getSchema(),
            /**
             * local host = ARGV[5];
            */
            serviceInstance.getHost(),
            /**
             * local port = ARGV[6];
            */
            String.valueOf(serviceInstance.getPort()),
            /**
             * local weight = ARGV[7];
            */
            String.valueOf(serviceInstance.getWeight())
        };
        
        
        String[] values = ServiceInstanceCodec.encodeMetadata(infoArgs, serviceInstance.getMetadata());
        
        return redisTemplate.execute(
                DiscoveryRedisScripts.SCRIPT_REGISTRY_REGISTER,
                Collections.singletonList(namespace),
                Arrays.asList(values)
            )
            .next();
    }
    
    @Override
    public Mono<Boolean> setService(String namespace, String serviceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceId), "serviceId can not be empty!");
        
        if (log.isInfoEnabled()) {
            log.info("setService - serviceId:[{}]  @ namespace:[{}].", serviceId, namespace);
        }
        return redisTemplate.execute(
                DiscoveryRedisScripts.SCRIPT_REGISTRY_SET_SERVICE,
                Collections.singletonList(namespace),
                Collections.singletonList(serviceId)
            )
            .next();
    }
    
    @Override
    public Mono<Boolean> removeService(String namespace, String serviceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceId), "serviceId can not be empty!");
        
        if (log.isWarnEnabled()) {
            log.warn("removeService - serviceId:[{}]  @ namespace:[{}].", serviceId, namespace);
        }
        return redisTemplate.execute(
                DiscoveryRedisScripts.SCRIPT_REGISTRY_REMOVE_SERVICE,
                Collections.singletonList(namespace),
                Collections.singletonList(serviceId)
            )
            .next();
    }
    
    @Override
    public Mono<Boolean> register(String namespace, ServiceInstance serviceInstance) {
        ensureNamespacedInstance(namespace, serviceInstance);
        
        ensureInstanceId(serviceInstance);
        if (log.isInfoEnabled()) {
            log.info("register - instanceId:[{}]  @ namespace:[{}].", serviceInstance.getInstanceId(), namespace);
        }
        
        addEphemeralInstance(namespace, serviceInstance);
        return register0(namespace, serviceInstance);
    }
    
    private void ensureNamespacedInstance(String namespace, ServiceInstance serviceInstance) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkNotNull(serviceInstance, "serviceInstance can not be null!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceInstance.getServiceId()), "serviceId can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceInstance.getSchema()), "schema can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceInstance.getHost()), "host can not be empty!");
    }
    
    private void ensureInstanceId(ServiceInstance serviceInstance) {
        if (Strings.isNullOrEmpty(serviceInstance.getInstanceId())) {
            serviceInstance.setInstanceId(InstanceIdGenerator.DEFAULT.generate(serviceInstance));
        }
    }
    
    private void addEphemeralInstance(String namespace, ServiceInstance serviceInstance) {
        if (!serviceInstance.isEphemeral()) {
            return;
        }
        registeredEphemeralInstances.put(NamespacedInstanceId.of(namespace, serviceInstance.getInstanceId()), serviceInstance);
    }
    
    private void removeEphemeralInstance(String namespace, String instanceId) {
        registeredEphemeralInstances.remove(NamespacedInstanceId.of(namespace, instanceId));
    }
    
    private void removeEphemeralInstance(String namespace, ServiceInstance serviceInstance) {
        if (!serviceInstance.isEphemeral()) {
            return;
        }
        
        registeredEphemeralInstances.remove(NamespacedInstanceId.of(namespace, serviceInstance.getInstanceId()));
    }
    
    @Override
    public Map<NamespacedInstanceId, ServiceInstance> getRegisteredEphemeralInstances() {
        return registeredEphemeralInstances;
    }
    
    @Override
    public Mono<Boolean> setMetadata(String namespace, String serviceId, String instanceId, String key, String value) {
        String[] values = {instanceId, key, value};
        return setMetadata0(namespace, instanceId, values);
    }
    
    @Override
    public Mono<Boolean> setMetadata(String namespace, String serviceId, String instanceId, Map<String, String> metadata) {
        String[] values = ServiceInstanceCodec.encodeMetadata(new String[] {instanceId}, metadata);
        return setMetadata0(namespace, instanceId, values);
    }
    
    private Mono<Boolean> setMetadata0(String namespace, String instanceId, String[] args) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(instanceId), "instanceId can not be empty!");
        
        if (log.isInfoEnabled()) {
            log.info("setMetadata - instanceId:[{}] @ namespace:[{}].", instanceId, namespace);
        }
        return redisTemplate.execute(
                DiscoveryRedisScripts.SCRIPT_REGISTRY_SET_METADATA,
                Collections.singletonList(namespace),
                Arrays.asList(args)
            )
            .next();
    }
    
    @Override
    public Mono<Boolean> renew(String namespace, ServiceInstance serviceInstance) {
        ensureNamespacedInstance(namespace, serviceInstance);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceInstance.getInstanceId()), "instanceId can not be empty!");
        
        if (log.isDebugEnabled()) {
            log.debug("renew - instanceId:[{}] @ namespace:[{}].", serviceInstance.getInstanceId(), namespace);
        }
        
        if (!serviceInstance.isEphemeral()) {
            if (log.isWarnEnabled()) {
                log.warn("renew - instanceId:[{}] @ namespace:[{}] is not ephemeral, can not renew.", serviceInstance.getInstanceId(), namespace);
            }
            return Mono.just(Boolean.FALSE);
        }

        return redisTemplate.execute(
                DiscoveryRedisScripts.SCRIPT_REGISTRY_RENEW,
                Collections.singletonList(namespace),
                Arrays.asList(serviceInstance.getInstanceId(), String.valueOf(registryProperties.getInstanceTtl()))
            )
            .flatMap(status -> {
                if (status <= 0) {
                    if (log.isWarnEnabled()) {
                        log.warn("renew - instanceId:[{}] @ namespace:[{}] status is [{}],register again.", serviceInstance.getInstanceId(), namespace, status);
                    }
                    return register(namespace, serviceInstance);
                }
                return Mono.just(Boolean.TRUE);
            })
            .next();
    }
    
    @Override
    public Mono<Boolean> deregister(String namespace, String serviceId, String instanceId) {
        if (log.isInfoEnabled()) {
            log.info("deregister - instanceId:[{}] @ namespace:[{}].", instanceId, namespace);
        }
        removeEphemeralInstance(namespace, instanceId);
        
        return deregister0(namespace, serviceId, instanceId);
    }
    
    @Override
    public Mono<Boolean> deregister(String namespace, ServiceInstance serviceInstance) {
        ensureInstanceId(serviceInstance);
        if (log.isInfoEnabled()) {
            log.info("deregister - instanceId:[{}] @ namespace:[{}].", serviceInstance.getInstanceId(), namespace);
        }
        
        removeEphemeralInstance(namespace, serviceInstance);
        return deregister0(namespace, serviceInstance.getServiceId(), serviceInstance.getInstanceId());
    }
    
    private Mono<Boolean> deregister0(String namespace, String serviceId, String instanceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceId), "serviceId can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(instanceId), "instanceId can not be empty!");
        return redisTemplate.execute(
                DiscoveryRedisScripts.SCRIPT_REGISTRY_DEREGISTER,
                Collections.singletonList(namespace),
                Arrays.asList(serviceId, instanceId)
            )
            .next();
    }
    
}
