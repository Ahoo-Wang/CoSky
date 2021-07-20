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

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
public interface ServiceTopology {

    String DEFAULT_CONSUMER_NAME = "_Client_";

    CompletableFuture<Void> addTopology(String producerNamespace, String producerServiceId);

    static String getConsumerName() {
        final ServiceInstance consumerServiceInstance = ServiceInstanceContext.CURRENT.getServiceInstance();

        if (Objects.isNull(consumerServiceInstance)) {
            return DEFAULT_CONSUMER_NAME;
        }
        return consumerServiceInstance.getServiceId();
    }

    static String getProducerName(String producerNamespace, String producerServiceId) {
        final String consumerNamespace = ServiceInstanceContext.CURRENT.getNamespace();

        if (producerNamespace.equals(consumerNamespace)) {
            return producerServiceId;
        }
        return producerNamespace + "." + producerServiceId;
    }
}
