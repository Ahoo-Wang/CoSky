package me.ahoo.govern.rest.controller;

import me.ahoo.govern.core.NamespaceService;
import me.ahoo.govern.core.NamespacedContext;
import org.springframework.web.bind.annotation.*;
import me.ahoo.govern.rest.support.RequestPathPrefix;

import java.util.Set;

/**
 * @author ahoo wang
 */
@RestController
@RequestMapping(RequestPathPrefix.NAMESPACES_PREFIX)
public class NamespaceController {

    private final NamespaceService namespaceService;

    public NamespaceController(NamespaceService namespaceService) {
        this.namespaceService = namespaceService;
    }


    @GetMapping
    public Set<String> getNamespaces() {
        return namespaceService.getNamespaces().join();
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
    public Boolean setNamespace(@PathVariable String namespace) {
        return namespaceService.setNamespace(namespace).join();
    }

    @DeleteMapping(RequestPathPrefix.NAMESPACES_NAMESPACE)
    public Boolean removeNamespace(@PathVariable String namespace) {
        return namespaceService.removeNamespace(namespace).join();
    }
}
