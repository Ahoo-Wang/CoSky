package me.ahoo.cosky.discovery.spring.cloud.registry;

import lombok.var;
import me.ahoo.cosky.discovery.spring.cloud.support.StatusConstants;
import me.ahoo.cosky.core.util.Futures;
import me.ahoo.cosky.discovery.RenewInstanceService;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;
import org.springframework.cloud.client.serviceregistry.endpoint.ServiceRegistryEndpoint;

/**
 * @author ahoo wang
 */
public class CoskyServiceRegistry implements ServiceRegistry<CoskyRegistration> {

    private final me.ahoo.cosky.discovery.ServiceRegistry serviceRegistry;
    private final RenewInstanceService renewInstanceService;
    private final CoskyRegistryProperties governRegistryProperties;

    public CoskyServiceRegistry(
            me.ahoo.cosky.discovery.ServiceRegistry serviceRegistry,
            RenewInstanceService renewInstanceService, CoskyRegistryProperties governRegistryProperties) {
        this.serviceRegistry = serviceRegistry;
        this.renewInstanceService = renewInstanceService;
        this.governRegistryProperties = governRegistryProperties;
    }

    /**
     * Registers the registration. A registration typically has information about an
     * instance, such as its hostname and port.
     *
     * @param registration registration meta data
     */
    @Override
    public void register(CoskyRegistration registration) {
        var instance = registration.of();
        var succeeded = Futures.getUnChecked(serviceRegistry.register(instance), governRegistryProperties.getTimeout());

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
    public void deregister(CoskyRegistration registration) {
        var instance = registration.of();
        var succeeded = Futures.getUnChecked(serviceRegistry.deregister(instance), governRegistryProperties.getTimeout());

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
    public void setStatus(CoskyRegistration registration, String status) {
        registration.getMetadata().put(StatusConstants.INSTANCE_STATUS_KEY, status);
        var instance = registration.of();
        var setMetadataFuture = serviceRegistry.setMetadata(instance.getServiceId(), instance.getInstanceId(), StatusConstants.INSTANCE_STATUS_KEY, status);
        Futures.getUnChecked(setMetadataFuture, governRegistryProperties.getTimeout());
    }

    /**
     * Gets the status of a particular registration.
     *
     * @param registration The registration to query.
     * @return The status of the registration.
     * @see ServiceRegistryEndpoint
     */
    @Override
    public <T> T getStatus(CoskyRegistration registration) {
        return (T) registration.getMetadata().get(StatusConstants.INSTANCE_STATUS_KEY);
    }
}
