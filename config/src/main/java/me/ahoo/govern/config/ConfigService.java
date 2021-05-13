package me.ahoo.govern.config;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
public interface ConfigService extends ConfigRollback {

    CompletableFuture<Set<String>> getConfigs();

    CompletableFuture<Set<String>> getConfigs(String namespace);

    CompletableFuture<Config> getConfig(String configId);

    CompletableFuture<Config> getConfig(String namespace, String configId);

    CompletableFuture<Boolean> setConfig(String configId, String data);

    CompletableFuture<Boolean> setConfig(String namespace, String configId, String data);

    CompletableFuture<Boolean> removeConfig(String configId);

    CompletableFuture<Boolean> removeConfig(String namespace, String configId);

}
