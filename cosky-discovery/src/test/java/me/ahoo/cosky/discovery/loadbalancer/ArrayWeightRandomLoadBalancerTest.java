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

package me.ahoo.cosky.discovery.loadbalancer;

import com.google.common.base.Stopwatch;
import lombok.extern.slf4j.Slf4j;

import me.ahoo.cosky.discovery.ServiceInstance;
import me.ahoo.cosky.discovery.TestServiceInstance;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author ahoo wang
 */
@Slf4j
class ArrayWeightRandomLoadBalancerTest {
    @Test
    public void choose() {
        String serviceId = "ServiceInstanceTree";
        ServiceInstance instance1 = TestServiceInstance.createInstance(serviceId);
        instance1.setWeight(2);
        ServiceInstance instance2 = TestServiceInstance.createInstance(serviceId);
        instance2.setWeight(3);
        ServiceInstance instance3 = TestServiceInstance.createInstance(serviceId);
        instance3.setWeight(5);
        List<ServiceInstance> instances = Arrays.asList(instance1,
            instance2,
            instance3);
        ArrayWeightRandomLoadBalancer.ArrayChooser arrayChooser = new ArrayWeightRandomLoadBalancer.ArrayChooser(instances);
        ServiceInstance instance = arrayChooser.choose();
        assertNotNull(instance);

        int totalTimes = 1000_000_0;
        int instance1Count = 0;
        int instance2Count = 0;
        int instance3Count = 0;
        Stopwatch stopwatch = Stopwatch.createStarted();
        for (int i = 0; i < totalTimes; i++) {
            ServiceInstance randomInstance = arrayChooser.choose();
            if (randomInstance.equals(instance1)) {
                instance1Count++;
            } else if (randomInstance.equals(instance2)) {
                instance2Count++;
            } else {
                instance3Count++;
            }
        }
        log.info("totalTimes:{} | [{}:{},{}:{},{}:{}] taken:[{}]",
                totalTimes,
                instance1Count, instance1Count * 1.0 / totalTimes,
                instance2Count, instance2Count * 1.0 / totalTimes,
                instance3Count, instance3Count * 1.0 / totalTimes,
                stopwatch.elapsed(TimeUnit.MILLISECONDS));

    }
}
