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

import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosky.discovery.ServiceInstance;
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery;

import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author ahoo wang
 */
@Slf4j
public class TreeWeightRandomLoadBalancer extends AbstractLoadBalancer<TreeWeightRandomLoadBalancer.TreeChooser> {


    public TreeWeightRandomLoadBalancer(ConsistencyRedisServiceDiscovery serviceDiscovery) {
        super(serviceDiscovery);
    }

    @Override
    protected TreeChooser createChooser(List<ServiceInstance> serviceInstances) {
        return new TreeChooser(serviceInstances);
    }

    public static class TreeChooser implements LoadBalancer.Chooser {

        private TreeMap<Integer, ServiceInstance> instanceTree;
        private int totalWeight;

        public TreeChooser(List<ServiceInstance> instanceList) {
            this.initTree(instanceList);
        }

        private void initTree(List<ServiceInstance> instanceList) {
            instanceTree = new TreeMap<>();
            int accWeight = ZERO;
            for (ServiceInstance instance : instanceList) {
                if (instance.getWeight() == ZERO) {
                    continue;
                }
                accWeight += instance.getWeight();
                instanceTree.put(accWeight, instance);
            }
            this.totalWeight = accWeight;
        }

        @Override
        public ServiceInstance choose() {
            if (instanceTree.size() == ZERO) {
                if (log.isWarnEnabled()) {
                    log.warn("choose - The size of connector instances is [{}]!", instanceTree.size());
                }
                return null;
            }

            if (ZERO == totalWeight) {
                log.warn("choose - The size of connector instances is [{}],but total weight is 0!", instanceTree.size());
                return null;
            }

            if (instanceTree.size() == ONE) {
                return instanceTree.firstEntry().getValue();
            }

            int randomVal = ThreadLocalRandom.current().nextInt(ZERO, totalWeight);
            NavigableMap<Integer, ServiceInstance> tailMap = instanceTree.tailMap(randomVal, false);
            return tailMap.firstEntry().getValue();
        }
    }
}
