package me.ahoo.cosky.discovery;

/**
 * @author ahoo wang
 */
public interface ServiceListenable {

    void addListener(NamespacedServiceId namespacedServiceId, ServiceChangedListener serviceChangedListener);

    void removeListener(NamespacedServiceId namespacedServiceId, ServiceChangedListener serviceChangedListener);
}