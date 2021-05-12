package me.ahoo.govern.discovery.loadbalancer;

import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.govern.discovery.ServiceInstance;
import me.ahoo.govern.discovery.redis.ConsistencyRedisServiceDiscovery;

import java.util.List;
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
            for (var instance : instanceList) {
                if (instance.getWeight() == ZERO) {
                    continue;
                }
                accWeight += instance.getWeight();
                instanceTree.put(accWeight, instance);
            }
            this.totalWeight = accWeight;
        }


        public ServiceInstance choose() {
            if (instanceTree.size() == ZERO) {
                if (log.isWarnEnabled()) {
                    log.warn("choose - The size of connector instances is [{}]!", instanceTree.size());
                }
                return null;
            }

            if (LoadBalancer.ZERO == totalWeight) {
                log.warn("choose - The size of connector instances is [{}],but total weight is 0!", instanceTree.size());
                return null;
            }

            if (instanceTree.size() == ONE) {
                return instanceTree.firstEntry().getValue();
            }

            var randomVal = ThreadLocalRandom.current().nextInt(ZERO, totalWeight);
            var tailMap = instanceTree.tailMap(randomVal, false);
            return tailMap.firstEntry().getValue();
        }
    }
}
