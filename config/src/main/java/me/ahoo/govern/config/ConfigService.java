package me.ahoo.govern.config;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
public interface ConfigService extends ConfigRollback {

    CompletableFuture<Set<String>> getConfigs();

    CompletableFuture<Config> getConfig(String configId);

    CompletableFuture<Boolean> setConfig(String configId, String data);

    CompletableFuture<Boolean> removeConfig(String configId);

}
