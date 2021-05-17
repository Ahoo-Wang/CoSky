package me.ahoo.govern.core;

/**
 * @author ahoo wang
 */
public class NamespacedProperties implements Namespaced {

    private String namespace = DEFAULT;

    @Override
    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }


}
