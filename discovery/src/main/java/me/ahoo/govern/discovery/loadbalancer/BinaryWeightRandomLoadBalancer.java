package me.ahoo.govern.discovery.loadbalancer;

import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.govern.discovery.ServiceInstance;
import me.ahoo.govern.discovery.redis.ConsistencyRedisServiceDiscovery;

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

        public ServiceInstance choose() {
            if (weightLine.length == ZERO) {
                if (log.isWarnEnabled()) {
                    log.warn("choose - The size of connector instances is [{}]!", weightLine.length);
                }
                return null;
            }


            if (LoadBalancer.ZERO == totalWeight) {
                log.warn("choose - The size of connector instances is [{}],but total weight is 0!", weightLine.length);
                return null;
            }

            if (weightLine.length == ONE) {
                return instanceList.get(ZERO);
            }

            final var randomValue = ThreadLocalRandom.current().nextInt(ONE, randomBound);
            if (randomValue == ONE) {
                return instanceList.get(ZERO);
            }
            if (randomValue == totalWeight) {
                return instanceList.get(maxLineIndex);
            }

            var instanceIdx = binarySearchLowIndex(randomValue);
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
