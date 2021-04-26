package me.ahoo.govern.config;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
public interface ConfigRollback {
    int HISTORY_SIZE = 10;

    CompletableFuture<Boolean> rollback(String configId, int targetVersion);

    CompletableFuture<List<ConfigVersion>> getConfigVersions(String configId);

    CompletableFuture<ConfigHistory> getConfigHistory(String configId, int version);
}
