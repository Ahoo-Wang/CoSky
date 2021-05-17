package me.ahoo.govern.rest.dto;

/**
 * @author ahoo wang
 */
public class GetStatResponse {
    private int namespaces;
    private int services;
    private int instances;
    private int configs;

    public int getNamespaces() {
        return namespaces;
    }

    public void setNamespaces(int namespaces) {
        this.namespaces = namespaces;
    }

    public int getServices() {
        return services;
    }

    public void setServices(int services) {
        this.services = services;
    }

    public int getInstances() {
        return instances;
    }

    public void setInstances(int instances) {
        this.instances = instances;
    }

    public int getConfigs() {
        return configs;
    }

    public void setConfigs(int configs) {
        this.configs = configs;
    }
}
