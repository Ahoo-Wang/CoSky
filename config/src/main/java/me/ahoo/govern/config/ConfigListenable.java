package me.ahoo.govern.config;

import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
public interface ConfigListenable {

    CompletableFuture<Boolean> addListener(String configId, ConfigChangedListener configChangedListener);

    CompletableFuture<Boolean> removeListener(String configId);
}
