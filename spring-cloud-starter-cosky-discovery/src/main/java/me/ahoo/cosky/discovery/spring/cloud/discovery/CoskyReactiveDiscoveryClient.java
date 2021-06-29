package me.ahoo.cosky.discovery.spring.cloud.discovery;

import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosky.discovery.ServiceDiscovery;
import org.reactivestreams.Publisher;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import reactor.core.publisher.Flux;

import java.util.Objects;

/**
 * @author ahoo wang
 */
@Slf4j
public class CoskyReactiveDiscoveryClient implements ReactiveDiscoveryClient {
    private final ServiceDiscovery serviceDiscovery;

    public CoskyReactiveDiscoveryClient(ServiceDiscovery serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
    }

    /**
     * A human-readable description of the implementation, used in HealthIndicator.
     *
     * @return The description.
     */
    @Override
    public String description() {
        return "CoSky Reactive Discovery Client";
    }

    /**
     * Gets all ServiceInstances associated with a particular serviceId.
     *
     * @param serviceId The serviceId to query.
     * @return A List of ServiceInstance.
     */
    @Override
    public Flux<ServiceInstance> getInstances(String serviceId) {
        return Flux.<ServiceInstance>defer(() -> (Publisher<ServiceInstance>) subscriber -> {
            serviceDiscovery.getInstances(serviceId).whenComplete((instances, error) -> {
                if (Objects.nonNull(error)) {
                    subscriber.onError(error);
                    log.error("getInstances - error.", error);
                    return;
                }
                instances.forEach(instance -> subscriber.onNext(new CoskyServiceInstance(instance)));
                subscriber.onComplete();
            });
        });
    }

    /**
     * @return All known service IDs.
     */
    @Override
    public Flux<String> getServices() {
        return Flux.<String>defer(() -> (Publisher<String>) subscriber -> {
            serviceDiscovery.getServices().whenComplete((instances, error) -> {
                if (Objects.nonNull(error)) {
                    subscriber.onError(error);
                    log.error("getServices - error.", error);
                    return;
                }
                instances.forEach(svc -> subscriber.onNext(svc));
                subscriber.onComplete();
            });
        });
    }
}