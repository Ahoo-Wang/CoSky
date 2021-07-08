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

package me.ahoo.cosky.discovery.loadbalancer;

import me.ahoo.cosky.discovery.ServiceInstance;
import me.ahoo.cosky.core.util.Futures;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
public interface LoadBalancer {
    int ZERO = 0;
    int ONE = 1;

    CompletableFuture<ServiceInstance> choose(String namespace, String serviceId);

    default ServiceInstance choose(String namespace, String serviceId, Duration timeout) {
        return Futures.getUnChecked(choose(namespace, serviceId), timeout);
    }

    interface Chooser {
        ServiceInstance choose();
    }
}
