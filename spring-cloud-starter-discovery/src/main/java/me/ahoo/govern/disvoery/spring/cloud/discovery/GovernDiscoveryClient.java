package me.ahoo.govern.disvoery.spring.cloud.discovery;

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
        return serviceDiscovery.getInstances(serviceId).join()
                .stream().map(serviceInstance -> new GovernServiceInstance(serviceInstance))
                .collect(Collectors.toList());
    }

    /**
     * @return All known service IDs.
     */
    @Override
    public List<String> getServices() {
        return serviceDiscovery.getServices().join().stream().collect(Collectors.toList());
    }

    @Override
    public int getOrder() {
        return governDiscoveryProperties.getOrder();
    }
}
