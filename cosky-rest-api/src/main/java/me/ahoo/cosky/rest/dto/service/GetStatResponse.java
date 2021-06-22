package me.ahoo.cosky.rest.dto.service;

/**
 * @author ahoo wang
 */
public class GetStatResponse {
    private int namespaces;
    private Services services;
    private int instances;
    private int configs;

    public int getNamespaces() {
        return namespaces;
    }

    public void setNamespaces(int namespaces) {
        this.namespaces = namespaces;
    }

    public Services getServices() {
        return services;
    }

    public void setServices(Services services) {
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

    public static class Services {
        private int total;
        private int health;

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }

        public int getHealth() {
            return health;
        }

        public void setHealth(int health) {
            this.health = health;
        }
    }
}
