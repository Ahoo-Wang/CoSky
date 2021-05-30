package me.ahoo.cosky.discovery.spring.cloud.registry;

import me.ahoo.cosky.discovery.ServiceInstance;
import org.springframework.cloud.client.serviceregistry.Registration;

import java.net.URI;
import java.util.Map;

/**
 * @author ahoo wang
 */
public class CoskyRegistration implements Registration {

    private final ServiceInstance serviceInstance;

    public CoskyRegistration(ServiceInstance serviceInstance) {
        this.serviceInstance = serviceInstance;
    }

    /**
     * @return The service ID as registered.
     */
    @Override
    public String getServiceId() {
        return serviceInstance.getServiceId();
    }

    /**
     * @return The hostname of the registered service instance.
     */
    @Override
    public String getHost() {
        return serviceInstance.getHost();
    }

    /**
     * @return The port of the registered service instance.
     */
    @Override
    public int getPort() {
        return serviceInstance.getPort();
    }

    public void setPort(int port) {
        serviceInstance.setPort(port);
    }

    /**
     * @return Whether the port of the registered service instance uses HTTPS.
     */
    @Override
    public boolean isSecure() {
        return serviceInstance.isSecure();
    }

    /**
     * @return The service URI address.
     */
    @Override
    public URI getUri() {
        return serviceInstance.parseUri();
    }

    /**
     * @return The key / value pair metadata associated with the service instance.
     */
    @Override
    public Map<String, String> getMetadata() {
        return serviceInstance.getMetadata();
    }

    public ServiceInstance of() {
        return serviceInstance;
    }
}
