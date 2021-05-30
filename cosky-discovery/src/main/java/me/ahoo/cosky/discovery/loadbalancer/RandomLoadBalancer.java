package me.ahoo.cosky.discovery.loadbalancer;

import me.ahoo.cosky.discovery.ServiceInstance;
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author ahoo wang
 */
public class RandomLoadBalancer extends AbstractLoadBalancer<RandomLoadBalancer.RandomChooser> {
    public RandomLoadBalancer(ConsistencyRedisServiceDiscovery serviceDiscovery) {
        super(serviceDiscovery);
    }

    @Override
    protected RandomChooser createChooser(List<ServiceInstance> serviceInstances) {
        return new RandomChooser(serviceInstances);
    }


    public static class RandomChooser implements LoadBalancer.Chooser {
        private final List<ServiceInstance> serviceInstances;

        public RandomChooser(List<ServiceInstance> serviceInstances) {
            this.serviceInstances = serviceInstances;
        }

        @Override
        public ServiceInstance choose() {
            if (serviceInstances.isEmpty()) {
                return null;
            }
            if (serviceInstances.size() == ONE) {
                return serviceInstances.get(ZERO);
            }
            int randomIdx = ThreadLocalRandom.current().nextInt(serviceInstances.size());
            return serviceInstances.get(randomIdx);
        }
    }
}
