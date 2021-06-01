package me.ahoo.cosky.discovery;

import com.google.common.base.Objects;
import me.ahoo.cosky.core.Namespaced;

/**
 * @author ahoo wang
 */
public class NamespacedInstanceId implements Namespaced {
    private final String namespace;
    private final String instanceId;

    public NamespacedInstanceId(String namespace, String instanceId) {
        this.namespace = namespace;
        this.instanceId = instanceId;
    }

    public static NamespacedInstanceId of(String namespace, String instanceId) {
        return new NamespacedInstanceId(namespace, instanceId);
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NamespacedInstanceId)) return false;
        NamespacedInstanceId that = (NamespacedInstanceId) o;
        return Objects.equal(namespace, that.namespace) && Objects.equal(instanceId, that.instanceId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(namespace, instanceId);
    }
}
