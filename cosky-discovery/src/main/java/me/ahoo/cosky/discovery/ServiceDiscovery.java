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

import java.util.List;

/**
 * @author ahoo wang
 */
public interface ServiceDiscovery {

    Mono<List<String>> getServices(String namespace);

    default Mono<List<String>> getServices() {
        return getServices(NamespacedContext.GLOBAL.getRequiredNamespace());
    }

    default Mono<List<ServiceInstance>> getInstances(String serviceId) {
        return getInstances(NamespacedContext.GLOBAL.getRequiredNamespace(), serviceId);
    }

    Mono<List<ServiceInstance>> getInstances(String namespace, String serviceId);

    Mono<ServiceInstance> getInstance(String namespace, String serviceId, String instanceId);

    Mono<Long> getInstanceTtl(String namespace, String serviceId, String instanceId);

}
