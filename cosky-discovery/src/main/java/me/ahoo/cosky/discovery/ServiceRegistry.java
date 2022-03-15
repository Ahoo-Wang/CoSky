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

package me.ahoo.cosky.discovery;

import me.ahoo.cosky.core.NamespacedContext;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Service Registry.
 *
 * @author ahoo wang
 */
public interface ServiceRegistry {
    
    Mono<Boolean> setService(String namespace, String serviceId);
    
    Mono<Boolean> removeService(String namespace, String serviceId);
    
    /**
     * 注册实例.
     *
     * @param serviceInstance serviceInstance
     */
    default Mono<Boolean> register(ServiceInstance serviceInstance) {
        return register(NamespacedContext.GLOBAL.getRequiredNamespace(), serviceInstance);
    }
    
    Mono<Boolean> register(String namespace, ServiceInstance serviceInstance);
    
    /**
     * 服务实例续期.
     *
     * @param serviceInstance serviceInstance
     * @return successful?
     */
    default Mono<Boolean> renew(ServiceInstance serviceInstance) {
        return renew(NamespacedContext.GLOBAL.getRequiredNamespace(), serviceInstance);
    }
    
    Mono<Boolean> renew(String namespace, ServiceInstance serviceInstance);
    
    default Mono<Boolean> deregister(ServiceInstance serviceInstance) {
        return deregister(NamespacedContext.GLOBAL.getRequiredNamespace(), serviceInstance);
    }
    
    Mono<Boolean> deregister(String namespace, ServiceInstance serviceInstance);
    
    default Mono<Boolean> deregister(String serviceId, String instanceId) {
        return deregister(NamespacedContext.GLOBAL.getRequiredNamespace(), serviceId, instanceId);
    }
    
    Mono<Boolean> deregister(String namespace, String serviceId, String instanceId);
    
    Map<NamespacedInstanceId, ServiceInstance> getRegisteredEphemeralInstances();
    
    default Mono<Boolean> setMetadata(String serviceId, String instanceId, String key, String value) {
        return setMetadata(NamespacedContext.GLOBAL.getRequiredNamespace(), serviceId, instanceId, key, value);
    }
    
    Mono<Boolean> setMetadata(String namespace, String serviceId, String instanceId, String key, String value);
    
    default Mono<Boolean> setMetadata(String serviceId, String instanceId, Map<String, String> metadata) {
        return setMetadata(NamespacedContext.GLOBAL.getRequiredNamespace(), serviceId, instanceId, metadata);
    }
    
    Mono<Boolean> setMetadata(String namespace, String serviceId, String instanceId, Map<String, String> metadata);
}
