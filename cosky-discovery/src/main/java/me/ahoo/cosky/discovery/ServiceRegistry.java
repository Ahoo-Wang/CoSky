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

package me.ahoo.cosky.discovery;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
public interface ServiceRegistry {


    CompletableFuture<Boolean> setService(String namespace, String serviceId);

    CompletableFuture<Boolean> removeService(String namespace, String serviceId);

    /**
     * 注册实例
     *
     * @param serviceInstance serviceInstance
     */
    CompletableFuture<Boolean> register(ServiceInstance serviceInstance);

    CompletableFuture<Boolean> register(String namespace, ServiceInstance serviceInstance);

    /**
     * 服务实例续期
     *
     * @param serviceInstance
     * @return successful?
     */
    CompletableFuture<Boolean> renew(ServiceInstance serviceInstance);

    CompletableFuture<Boolean> renew(String namespace, ServiceInstance serviceInstance);

    CompletableFuture<Boolean> deregister(ServiceInstance serviceInstance);

    CompletableFuture<Boolean> deregister(String namespace, ServiceInstance serviceInstance);

    CompletableFuture<Boolean> deregister(String serviceId, String instanceId);

    CompletableFuture<Boolean> deregister(String namespace, String serviceId, String instanceId);

    Map<NamespacedInstanceId, ServiceInstance> getRegisteredEphemeralInstances();

    CompletableFuture<Boolean> setMetadata(String serviceId, String instanceId, String key, String value);

    CompletableFuture<Boolean> setMetadata(String namespace, String serviceId, String instanceId, String key, String value);

    CompletableFuture<Boolean> setMetadata(String serviceId, String instanceId, Map<String, String> metadata);

    CompletableFuture<Boolean> setMetadata(String namespace, String serviceId, String instanceId, Map<String, String> metadata);
}
