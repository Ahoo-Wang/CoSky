package me.ahoo.govern.disvoery.spring.cloud.registry;

import lombok.var;
import me.ahoo.govern.discovery.RenewInstanceService;
import me.ahoo.govern.disvoery.spring.cloud.support.StatusConstants;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;
import org.springframework.cloud.client.serviceregistry.endpoint.ServiceRegistryEndpoint;

/**
 * @author ahoo wang
 */
public class GovernServiceRegistry implements ServiceRegistry<GovernRegistration> {

    private final me.ahoo.govern.discovery.ServiceRegistry serviceRegistry;
    private final RenewInstanceService renewInstanceService;

    public GovernServiceRegistry(
            me.ahoo.govern.discovery.ServiceRegistry serviceRegistry, RenewInstanceService renewInstanceService) {
        this.serviceRegistry = serviceRegistry;
        this.renewInstanceService = renewInstanceService;
    }

    /**
     * Registers the registration. A registration typically has information about an
     * instance, such as its hostname and port.
     *
     * @param registration registration meta data
     */
    @Override
    public void register(GovernRegistration registration) {
        var instance = registration.of();
        var succeeded = serviceRegistry.register(instance).join();
        if (!succeeded) {
            throw new RuntimeException("Service registration failed");
        }
        renewInstanceService.start();
    }

    /**
     * Deregisters the registration.
     *
     * @param registration registration meta data
     */
    @Override
    public void deregister(GovernRegistration registration) {
        var instance = registration.of();
        var succeeded = serviceRegistry.deregister(instance).join();
        if (!succeeded) {
            throw new RuntimeException("Service deregister failed");
        }
    }

    /**
     * Closes the ServiceRegistry. This is a lifecycle method.
     */
    @Override
    public void close() {
        renewInstanceService.stop();
    }

    /**
     * Sets the status of the registration. The status values are determined by the
     * individual implementations.
     *
     * @param registration The registration to update.
     * @param status       The status to set.
     * @see ServiceRegistryEndpoint
     */
    @Override
    public void setStatus(GovernRegistration registration, String status) {
        registration.getMetadata().put(StatusConstants.INSTANCE_STATUS_KEY, status);
        var instance = registration.of();
        serviceRegistry.setMetadata(instance.getServiceId(), instance.getInstanceId(), StatusConstants.INSTANCE_STATUS_KEY, status).join();
    }

    /**
     * Gets the status of a particular registration.
     *
     * @param registration The registration to query.
     * @return The status of the registration.
     * @see ServiceRegistryEndpoint
     */
    @Override
    public <T> T getStatus(GovernRegistration registration) {
        return (T) registration.getMetadata().get(StatusConstants.INSTANCE_STATUS_KEY);
    }
}
