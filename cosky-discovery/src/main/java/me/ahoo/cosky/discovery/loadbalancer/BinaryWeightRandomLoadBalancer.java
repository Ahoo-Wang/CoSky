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

import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery;
import me.ahoo.cosky.discovery.ServiceInstance;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author ahoo wang
 */
@Slf4j
public class BinaryWeightRandomLoadBalancer extends AbstractLoadBalancer<BinaryWeightRandomLoadBalancer.BinaryChooser> {

    public BinaryWeightRandomLoadBalancer(ConsistencyRedisServiceDiscovery serviceDiscovery) {
        super(serviceDiscovery);
    }

    @Override
    protected BinaryChooser createChooser(List<ServiceInstance> serviceInstances) {
        return new BinaryChooser(serviceInstances);
    }

    public static class BinaryChooser implements LoadBalancer.Chooser {

        private final List<ServiceInstance> instanceList;
        private int totalWeight;
        private int randomBound;
        private int[] weightLine;
        private final int maxLineIndex;

        public BinaryChooser(List<ServiceInstance> instanceList) {
            this.maxLineIndex = instanceList.size() - 1;
            this.instanceList = instanceList;
            initLine(instanceList);
        }

        private void initLine(List<ServiceInstance> instanceList) {
            weightLine = new int[instanceList.size()];
            int accWeight = ZERO;
            for (int i = 0; i < instanceList.size(); i++) {
                int instanceWeight = instanceList.get(i).getWeight();
                if (instanceWeight == ZERO) {
                    continue;
                }
                accWeight += instanceWeight;
                weightLine[i] = accWeight;
            }
            this.totalWeight = accWeight;
            this.randomBound = totalWeight + ONE;
        }
        @Override
        public ServiceInstance choose() {
            if (weightLine.length == ZERO) {
                if (log.isWarnEnabled()) {
                    log.warn("choose - The size of connector instances is [{}]!", weightLine.length);
                }
                return null;
            }


            if (ZERO == totalWeight) {
                log.warn("choose - The size of connector instances is [{}],but total weight is 0!", weightLine.length);
                return null;
            }

            if (weightLine.length == ONE) {
                return instanceList.get(ZERO);
            }

            final int randomValue = ThreadLocalRandom.current().nextInt(ONE, randomBound);
            if (randomValue == ONE) {
                return instanceList.get(ZERO);
            }
            if (randomValue == totalWeight) {
                return instanceList.get(maxLineIndex);
            }

            int instanceIdx = binarySearchLowIndex(randomValue);
            return instanceList.get(instanceIdx);
        }

        private int binarySearchLowIndex(int randomValue) {
            int idx = Arrays.binarySearch(weightLine, randomValue);
            if (idx < 0) {
                idx = -idx - 1;
            }
            return idx;
        }
    }
}
