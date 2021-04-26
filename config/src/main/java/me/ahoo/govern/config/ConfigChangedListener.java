package me.ahoo.govern.config;

/**
 * @author ahoo wang
 */
public interface ConfigChangedListener {
    void onChange(String configId, String message);
}
