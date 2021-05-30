package me.ahoo.cosky.discovery;

/**
 * @author ahoo wang
 */
public interface ServiceChangedListener {
    String REGISTER = "register";
    String DEREGISTER = "deregister";
    String EXPIRED = "expired";
    String RENEW = "renew";
    String SET_METADATA = "set_metadata";

    void onChange(NamespacedServiceId namespacedServiceId, String op);
}
