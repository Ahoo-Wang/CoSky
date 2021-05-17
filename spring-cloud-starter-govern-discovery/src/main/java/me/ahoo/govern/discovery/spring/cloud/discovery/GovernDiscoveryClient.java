package me.ahoo.govern.discovery.spring.cloud.discovery;

import me.ahoo.govern.core.util.Futures;
import me.ahoo.govern.discovery.ServiceDiscovery;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author ahoo wang
 */
public class GovernDiscoveryClient implements DiscoveryClient {
    private final ServiceDiscovery serviceDiscovery;
    private final GovernDiscoveryProperties governDiscoveryProperties;

    public GovernDiscoveryClient(ServiceDiscovery serviceDiscovery, GovernDiscoveryProperties governDiscoveryProperties) {
        this.serviceDiscovery = serviceDiscovery;
        this.governDiscoveryProperties = governDiscoveryProperties;
    }

    /**
     * A human-readable description of the implementation, used in HealthIndicator.
     *
     * @return The description.
     */
    @Override
    public String description() {
        return "Govern Service Discovery Client On Redis";
    }

    /**
     * Gets all ServiceInstances associated with a particular serviceId.
     *
     * @param serviceId The serviceId to query.
     * @return A List of ServiceInstance.
     */
    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        return Futures.getUnChecked(serviceDiscovery.getInstances(serviceId), governDiscoveryProperties.getTimeout())
                .stream().map(serviceInstance -> new GovernServiceInstance(serviceInstance))
                .collect(Collectors.toList());
    }

    /**
     * @return All known service IDs.
     */
    @Override
    public List<String> getServices() {
        return Futures.getUnChecked(serviceDiscovery.getServices(), governDiscoveryProperties.getTimeout())
                .stream().collect(Collectors.toList());
    }

    @Override
    public int getOrder() {
        return governDiscoveryProperties.getOrder();
    }
}
