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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosky.core.NamespacedContext;
import me.ahoo.cosky.discovery.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author ahoo wang
 */
@Slf4j
public class RedisServiceRegistry implements ServiceRegistry {

    private final RegistryProperties registryProperties;
    private final RedisClusterReactiveCommands<String, String> redisCommands;
    private final ConcurrentHashMap<NamespacedInstanceId, ServiceInstance> registeredEphemeralInstances;

    public RedisServiceRegistry(RegistryProperties registryProperties,
                                RedisClusterReactiveCommands<String, String> redisCommands) {
        this.registeredEphemeralInstances = new ConcurrentHashMap<>();
        this.registryProperties = registryProperties;
        this.redisCommands = redisCommands;
    }

    private Mono<Boolean> register0(String namespace, String scriptSha, ServiceInstance serviceInstance) {
        /**
         * KEYS[1]
         */
        String[] keys = {namespace};
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

        return redisCommands.evalsha(scriptSha, ScriptOutputType.BOOLEAN, keys, values)
                .map(result -> (Boolean) result)
                .next();
    }

    @Override
    public Mono<Boolean> setService(String namespace, String serviceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceId), "serviceId can not be empty!");

        if (log.isInfoEnabled()) {
            log.info("setService - serviceId:[{}]  @ namespace:[{}].", serviceId, namespace);
        }

        return DiscoveryRedisScripts.doRegistrySetService(redisCommands,
                sha -> redisCommands.evalsha(sha, ScriptOutputType.BOOLEAN, new String[]{namespace}, serviceId)
                        .cast(Boolean.class)
                        .next()
        );
    }

    @Override
    public Mono<Boolean> removeService(String namespace, String serviceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceId), "serviceId can not be empty!");

        if (log.isWarnEnabled()) {
            log.warn("removeService - serviceId:[{}]  @ namespace:[{}].", serviceId, namespace);
        }

        return DiscoveryRedisScripts.doRegistryRemoveService(redisCommands,
                sha -> redisCommands.evalsha(sha, ScriptOutputType.BOOLEAN, new String[]{namespace}, serviceId)
                        .cast(Boolean.class)
                        .next()
        );
    }

    @Override
    public Mono<Boolean> register(String namespace, ServiceInstance serviceInstance) {
        ensureNamespacedInstance(namespace, serviceInstance);

        ensureInstanceId(serviceInstance);
        if (log.isInfoEnabled()) {
            log.info("register - instanceId:[{}]  @ namespace:[{}].", serviceInstance.getInstanceId(), namespace);
        }

        addEphemeralInstance(namespace, serviceInstance);
        return DiscoveryRedisScripts.doRegistryRegister(redisCommands, sha -> register0(namespace, sha, serviceInstance));
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
        String[] values = ServiceInstanceCodec.encodeMetadata(new String[]{instanceId}, metadata);
        return setMetadata0(namespace, instanceId, values);
    }

    private Mono<Boolean> setMetadata0(String namespace, String instanceId, String[] args) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(instanceId), "instanceId can not be empty!");

        if (log.isInfoEnabled()) {
            log.info("setMetadata - instanceId:[{}] @ namespace:[{}].", instanceId, namespace);
        }
        String[] keys = {namespace};
        return DiscoveryRedisScripts.doRegistrySetMetadata(redisCommands, sha ->
                redisCommands.evalsha(sha, ScriptOutputType.BOOLEAN, keys, args)
                        .cast(Boolean.class)
                        .next()
        );
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
        String[] keys = {namespace};
        String[] values = {serviceInstance.getInstanceId(), String.valueOf(registryProperties.getInstanceTtl())};
        return DiscoveryRedisScripts.doRegistryRenew(redisCommands, sha ->
                redisCommands.evalsha(sha, ScriptOutputType.INTEGER, keys, values)
                        .cast(Long.class)
                        .next()
        ).flatMap(status -> {
            if (status <= 0) {
                if (log.isWarnEnabled()) {
                    log.warn("renew - instanceId:[{}] @ namespace:[{}] status is [{}],register again.", serviceInstance.getInstanceId(), namespace, status);
                }
                return register(namespace, serviceInstance);
            }
            return Mono.just(Boolean.TRUE);
        });
    }

    @Override
    public Mono<Boolean> deregister(String namespace, String serviceId, String instanceId) {
        if (log.isInfoEnabled()) {
            log.info("deregister - instanceId:[{}] @ namespace:[{}].", instanceId, namespace);
        }
        removeEphemeralInstance(namespace, instanceId);

        return deregister0(namespace, serviceId, instanceId);
    }

    private Mono<Boolean> deregister0(String namespace, String serviceId, String instanceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceId), "serviceId can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(instanceId), "instanceId can not be empty!");

        return DiscoveryRedisScripts.doRegistryDeregister(redisCommands, sha -> {
            String[] keys = {namespace};
            String[] values = {serviceId, instanceId};
            return redisCommands.evalsha(sha, ScriptOutputType.BOOLEAN, keys, values)
                    .cast(Boolean.class)
                    .next();
        });
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
}
