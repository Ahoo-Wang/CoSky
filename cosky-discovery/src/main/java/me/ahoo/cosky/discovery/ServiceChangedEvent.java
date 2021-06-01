package me.ahoo.cosky.discovery;

/**
 * @author ahoo wang
 */
public class ServiceChangedEvent {

    public static final String REGISTER = "register";
    public static final String DEREGISTER = "deregister";
    public static final String EXPIRED = "expired";
    public static final String RENEW = "renew";
    public static final String SET_METADATA = "set_metadata";

    private final NamespacedServiceId namespacedServiceId;
    private final String op;
    private final Instance instance;

    public ServiceChangedEvent(NamespacedServiceId namespacedServiceId, String op, Instance instance) {
        this.namespacedServiceId = namespacedServiceId;
        this.op = op;
        this.instance = instance;
    }

    public NamespacedServiceId getNamespacedServiceId() {
        return namespacedServiceId;
    }

    public String getOp() {
        return op;
    }

    public Instance getInstance() {
        return instance;
    }

    public static ServiceChangedEvent of(NamespacedServiceId namespacedServiceId, String op, Instance instance) {
        return new ServiceChangedEvent(namespacedServiceId, op, instance);
    }

}
