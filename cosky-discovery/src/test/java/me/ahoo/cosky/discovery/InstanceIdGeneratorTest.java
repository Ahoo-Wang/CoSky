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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author ahoo wang
 */
public class InstanceIdGeneratorTest {
    @Test
    public void generate() {
        ServiceInstance serviceInstance = new ServiceInstance();
        serviceInstance.setServiceId("order_service");
        serviceInstance.setSchema("http");
        serviceInstance.setHost("127.0.0.1");
        serviceInstance.setPort(8080);

        String expected = "order_service@http#127.0.0.1#8080";
        String actual = InstanceIdGenerator.DEFAULT.generate(serviceInstance);

        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void of() {

        String instanceId = "order_service@http#127.0.0.1#8080";
        Instance instance = InstanceIdGenerator.DEFAULT.of(instanceId);

        Assertions.assertEquals(instanceId, instance.getInstanceId());
        Assertions.assertEquals("order_service", instance.getServiceId());
        Assertions.assertEquals("http", instance.getSchema());
        Assertions.assertEquals("127.0.0.1", instance.getHost());
        Assertions.assertEquals(8080, instance.getPort());

    }


}
