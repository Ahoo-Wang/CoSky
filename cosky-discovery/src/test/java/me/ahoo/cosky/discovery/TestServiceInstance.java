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

import lombok.var;

import java.util.concurrent.ThreadLocalRandom;

/**
 * @author ahoo wang
 */
public final class TestServiceInstance {
    public final static ServiceInstance TEST_INSTANCE = new ServiceInstance();
    public final static ServiceInstance TEST_FIXED_INSTANCE = new ServiceInstance();

    static {
        TEST_INSTANCE.setServiceId("test_service");
        TEST_INSTANCE.setSchema("http");
        TEST_INSTANCE.setHost("127.0.0.1");
        TEST_INSTANCE.setPort(8080);
        TEST_INSTANCE.setInstanceId(InstanceIdGenerator.DEFAULT.generate(TEST_INSTANCE));
        TEST_INSTANCE.getMetadata().put("from", "test");

        TEST_FIXED_INSTANCE.setServiceId("test_fixed_service");
        TEST_FIXED_INSTANCE.setSchema("http");
        TEST_FIXED_INSTANCE.setHost("127.0.0.2");
        TEST_FIXED_INSTANCE.setPort(8080);
        TEST_FIXED_INSTANCE.setInstanceId(InstanceIdGenerator.DEFAULT.generate(TEST_FIXED_INSTANCE));
        TEST_FIXED_INSTANCE.setEphemeral(false);
        TEST_FIXED_INSTANCE.getMetadata().put("from", "test");
    }


    public static ServiceInstance createInstance(String serviceId) {
        var instance = new ServiceInstance();
        instance.setServiceId(serviceId);
        instance.setSchema("http");
        instance.setHost("127.0.0.2");
        instance.setPort(ThreadLocalRandom.current().nextInt(65535));
        instance.setEphemeral(false);
        instance.getMetadata().put("from", "test");
        instance.setInstanceId(InstanceIdGenerator.DEFAULT.generate(instance));
        return instance;
    }
}
