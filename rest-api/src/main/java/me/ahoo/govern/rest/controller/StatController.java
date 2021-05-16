package me.ahoo.govern.rest.controller;

import lombok.var;
import me.ahoo.govern.config.ConfigService;
import me.ahoo.govern.core.NamespaceService;
import me.ahoo.govern.discovery.ServiceDiscovery;
import me.ahoo.govern.discovery.ServiceStatistic;
import me.ahoo.govern.rest.dto.GetStatResponse;
import me.ahoo.govern.rest.support.RequestPathPrefix;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
@CrossOrigin("*")
@RestController
@RequestMapping(RequestPathPrefix.STAT_PREFIX)
public class StatController {

    private final NamespaceService namespaceService;
    private final ServiceDiscovery serviceDiscovery;
    private final ConfigService configService;
    private final ServiceStatistic serviceStatistic;

    public StatController(NamespaceService namespaceService, ServiceDiscovery serviceDiscovery, ConfigService configService, ServiceStatistic serviceStatistic) {
        this.namespaceService = namespaceService;
        this.serviceDiscovery = serviceDiscovery;
        this.configService = configService;
        this.serviceStatistic = serviceStatistic;
    }

    @GetMapping
    public CompletableFuture<GetStatResponse> getStat(@PathVariable String namespace) {
        var statResponse = new GetStatResponse();

        var setNamespacesFuture = namespaceService.getNamespaces().thenAccept(namespaces -> statResponse.setNamespaces(namespaces.size()));
        var setServicesFuture = serviceDiscovery.getServices(namespace).thenAccept(services -> statResponse.setServices(services.size()));
        var setInstancesFuture = serviceStatistic.getInstanceCount(namespace).thenAccept(count -> statResponse.setInstances(count.intValue()));
        var setConfigsFuture = configService.getConfigs(namespace).thenAccept(configs -> statResponse.setConfigs(configs.size()));
        return CompletableFuture.allOf(setNamespacesFuture, setServicesFuture, setInstancesFuture, setConfigsFuture).thenApply((nil) -> statResponse);
    }
}
