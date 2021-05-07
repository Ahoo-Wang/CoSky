package me.ahoo.govern.discovery;

/**
 * @author ahoo wang
 */
public interface ServiceEventType {
    String REGISTER = "register";
    String DEREGISTER = "deregister";
    String EXPIRED = "expired";
    String RENEW = "renew";
    String SET_METADATA = "set_metadata";
}
