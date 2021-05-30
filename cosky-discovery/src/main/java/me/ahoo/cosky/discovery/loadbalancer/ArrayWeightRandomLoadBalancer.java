package me.ahoo.cosky.discovery.loadbalancer;

import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery;
import me.ahoo.cosky.discovery.ServiceInstance;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
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
                this.totalWeight = instanceList.stream().map(node -> node.getWeight()).reduce(Integer::sum).get();
            }
            instanceLine = this.toLine(instanceList);
        }

        private ServiceInstance[] toLine(List<ServiceInstance> instanceList) {
            var line = new ServiceInstance[totalWeight];
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

            var randomValue = ThreadLocalRandom.current().nextInt(0, totalWeight);
            var instance = instanceLine[randomValue];
            return instance;
        }
    }
}
