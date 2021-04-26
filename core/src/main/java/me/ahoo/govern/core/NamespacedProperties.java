package me.ahoo.govern.core;

/**
 * @author ahoo wang
 */
public class NamespacedProperties implements Namespaced {

    private String namespace = Consts.GOVERN;

    @Override
    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
}
