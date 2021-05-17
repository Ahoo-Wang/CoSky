package me.ahoo.govern.rest.controller;

import me.ahoo.govern.core.NamespaceService;
import me.ahoo.govern.core.NamespacedContext;
import me.ahoo.govern.rest.support.RequestPathPrefix;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
@CrossOrigin("*")
@RestController
@RequestMapping(RequestPathPrefix.NAMESPACES_PREFIX)
public class NamespaceController {

    private final NamespaceService namespaceService;

    public NamespaceController(NamespaceService namespaceService) {
        this.namespaceService = namespaceService;
    }


    @GetMapping
    public CompletableFuture<Set<String>> getNamespaces() {
        return namespaceService.getNamespaces();
    }

    @GetMapping(RequestPathPrefix.NAMESPACES_CURRENT)
    public String current() {
        return NamespacedContext.GLOBAL.getNamespace();
    }

    @PutMapping(RequestPathPrefix.NAMESPACES_CURRENT_NAMESPACE)
    public void setCurrentContextNamespace(@PathVariable String namespace) {
        NamespacedContext.GLOBAL.setCurrentContextNamespace(namespace);
    }

    @PutMapping(RequestPathPrefix.NAMESPACES_NAMESPACE)
    public CompletableFuture<Boolean> setNamespace(@PathVariable String namespace) {
        return namespaceService.setNamespace(namespace);
    }

    @DeleteMapping(RequestPathPrefix.NAMESPACES_NAMESPACE)
    public CompletableFuture<Boolean> removeNamespace(@PathVariable String namespace) {
        return namespaceService.removeNamespace(namespace);
    }
}
