package me.ahoo.cosky.discovery;

import com.google.common.base.Objects;
import me.ahoo.cosky.core.Namespaced;

/**
 * @author ahoo wang
 */
public class NamespacedServiceId implements Namespaced {
    private final String namespace;
    private final String serviceId;

    public NamespacedServiceId(String namespace, String serviceId) {
        this.namespace = namespace;
        this.serviceId = serviceId;
    }

    public static NamespacedServiceId of(String namespace, String serviceId) {
        return new NamespacedServiceId(namespace, serviceId);
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    public String getServiceId() {
        return serviceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NamespacedServiceId)) return false;
        NamespacedServiceId that = (NamespacedServiceId) o;
        return Objects.equal(namespace, that.namespace) && Objects.equal(serviceId, that.serviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(namespace, serviceId);
    }
}
