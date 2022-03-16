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

import me.ahoo.cosid.util.MockIdGenerator;

import reactor.test.StepVerifier;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * @author ahoo wang
 */
public final class TestServiceInstance {
    
    public static ServiceInstance randomInstance() {
        return createInstance(MockIdGenerator.INSTANCE.generateAsString());
    }
    
    public static ServiceInstance createInstance(String serviceId) {
        ServiceInstance instance = new ServiceInstance();
        instance.setServiceId(serviceId);
        instance.setSchema("http");
        instance.setHost("127.0.0.1");
        instance.setPort(ThreadLocalRandom.current().nextInt(65535));
        instance.setInstanceId(InstanceIdGenerator.DEFAULT.generate(instance));
        instance.getMetadata().put("from", "test");
        return instance;
    }
    
    public static ServiceInstance randomFixedInstance() {
        ServiceInstance randomInstance = randomInstance();
        randomInstance.setEphemeral(false);
        return randomInstance;
    }
    
    public static void registerRandomInstanceAndTestThenDeregister(String namespace, ServiceRegistry serviceRegistry, Consumer<ServiceInstance> doTest) {
        ServiceInstance randomInstance = randomInstance();
        StepVerifier.create(serviceRegistry.register(namespace, randomInstance))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
        doTest.accept(randomInstance);
        StepVerifier.create(serviceRegistry.deregister(namespace, randomInstance))
            .expectNext(Boolean.TRUE)
            .verifyComplete();
    }
}
