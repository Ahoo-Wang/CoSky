package me.ahoo.cosky.discovery;

import com.google.common.base.Objects;
import me.ahoo.cosky.core.Namespaced;

/**
 * @author ahoo wang
 */
public class NamespacedServiceInstance implements Namespaced {
    private final String namespace;
    private final ServiceInstance serviceInstance;

    public NamespacedServiceInstance(String namespace, ServiceInstance serviceInstance) {
        this.namespace = namespace;
        this.serviceInstance = serviceInstance;
    }

    public static NamespacedServiceInstance of(String namespace, ServiceInstance serviceInstance) {
        return new NamespacedServiceInstance(namespace, serviceInstance);
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    public ServiceInstance getServiceInstance() {
        return serviceInstance;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NamespacedServiceInstance)) return false;
        NamespacedServiceInstance that = (NamespacedServiceInstance) o;
        return Objects.equal(namespace, that.namespace) && Objects.equal(serviceInstance, that.serviceInstance);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(namespace, serviceInstance);
    }
}
