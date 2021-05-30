package me.ahoo.cosky.config;

import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
public interface ConfigListenable {

    CompletableFuture<Boolean> addListener(NamespacedConfigId namespacedConfigId, ConfigChangedListener configChangedListener);

    CompletableFuture<Boolean> removeListener(NamespacedConfigId namespacedConfigId, ConfigChangedListener configChangedListener);
}
