package me.ahoo.cosky.config;

import com.google.common.base.Objects;
import me.ahoo.cosky.core.Namespaced;

/**
 * @author ahoo wang
 */
public class NamespacedConfigId implements Namespaced {
    private final String namespace;
    private final String configId;

    public NamespacedConfigId(String namespace, String configId) {
        this.namespace = namespace;
        this.configId = configId;
    }

    public static NamespacedConfigId of(String namespace, String configId) {
        return new NamespacedConfigId(namespace, configId);
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    public String getConfigId() {
        return configId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NamespacedConfigId)) return false;
        NamespacedConfigId that = (NamespacedConfigId) o;
        return Objects.equal(namespace, that.namespace) && Objects.equal(configId, that.configId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(namespace, configId);
    }
}
