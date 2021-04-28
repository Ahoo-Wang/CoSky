package me.ahoo.govern.config;

import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
public interface ConfigListenable {

    CompletableFuture<Boolean> addListener(String configId, ConfigChangedListener configChangedListener);

    CompletableFuture<Boolean> addListener(String namespace, String configId, ConfigChangedListener configChangedListener);

    CompletableFuture<Boolean> removeListener(String configId);

    CompletableFuture<Boolean> removeListener(String namespace, String configId);
}
