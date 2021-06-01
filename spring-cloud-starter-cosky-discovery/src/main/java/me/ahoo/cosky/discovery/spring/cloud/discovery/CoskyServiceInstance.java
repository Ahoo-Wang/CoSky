package me.ahoo.cosky.discovery.spring.cloud.discovery;

import org.springframework.cloud.client.ServiceInstance;

import java.net.URI;
import java.util.Map;

/**
 * @author ahoo wang
 */
public class CoskyServiceInstance implements ServiceInstance {
    private final me.ahoo.cosky.discovery.ServiceInstance serviceInstance;

    public CoskyServiceInstance(me.ahoo.cosky.discovery.ServiceInstance serviceInstance) {
        this.serviceInstance = serviceInstance;
    }

    /**
     * @return The unique instance ID as registered.
     */
    @Override
    public String getInstanceId() {
        return serviceInstance.getInstanceId();
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

    /**
     * @return The scheme of the service instance.
     */
    @Override
    public String getScheme() {
        return serviceInstance.getSchema();
    }

    public me.ahoo.cosky.discovery.ServiceInstance of() {
        return serviceInstance;
    }
}
