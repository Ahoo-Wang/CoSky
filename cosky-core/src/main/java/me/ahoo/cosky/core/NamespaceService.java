package me.ahoo.cosky.core;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
public interface NamespaceService {

    CompletableFuture<Set<String>> getNamespaces();

    CompletableFuture<Boolean> setNamespace(String namespace);

    CompletableFuture<Boolean> removeNamespace(String namespace);
}
