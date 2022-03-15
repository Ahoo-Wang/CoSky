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

import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery;
import me.ahoo.cosky.discovery.ServiceInstance;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Array Weight Random Load Balancer.
 *
 * @author ahoo wang
 */
@Slf4j
public class ArrayWeightRandomLoadBalancer extends AbstractLoadBalancer<ArrayWeightRandomLoadBalancer.ArrayChooser> {
    public ArrayWeightRandomLoadBalancer(ConsistencyRedisServiceDiscovery serviceDiscovery) {
        super(serviceDiscovery);
    }
    
    @Override
    protected ArrayChooser createChooser(List<ServiceInstance> serviceInstances) {
        return new ArrayChooser(serviceInstances);
    }
    
    public static class ArrayChooser implements LoadBalancer.Chooser {
        
        private final ServiceInstance[] instanceLine;
        private final int totalWeight;
        
        public ArrayChooser(List<ServiceInstance> instanceList) {
            if (instanceList.isEmpty()) {
                this.totalWeight = ZERO;
            } else {
                this.totalWeight = instanceList.stream().map(ServiceInstance::getWeight).reduce(Integer::sum).get();
            }
            instanceLine = this.toLine(instanceList);
        }
        
        private ServiceInstance[] toLine(List<ServiceInstance> instanceList) {
            ServiceInstance[] line = new ServiceInstance[totalWeight];
            int startX = ZERO;
            for (ServiceInstance connectorInstance : instanceList) {
                int weightLength = connectorInstance.getWeight();
                int idx = ZERO;
                while (idx < weightLength) {
                    line[startX] = connectorInstance;
                    idx++;
                    startX++;
                }
            }
            return line;
        }
        
        @Override
        public ServiceInstance choose() {
            if (instanceLine.length == ZERO) {
                if (log.isWarnEnabled()) {
                    log.warn("choose - The size of connector instances is [{}]!", instanceLine.length);
                }
                return null;
            }
            
            
            if (ZERO == totalWeight) {
                log.warn("choose - The size of connector instances is [{}],but total weight is 0!", instanceLine.length);
                return null;
            }
            
            if (instanceLine.length == ONE) {
                return instanceLine[ZERO];
            }
            
            int randomValue = ThreadLocalRandom.current().nextInt(0, totalWeight);
            ServiceInstance instance = instanceLine[randomValue];
            return instance;
        }
    }
}
