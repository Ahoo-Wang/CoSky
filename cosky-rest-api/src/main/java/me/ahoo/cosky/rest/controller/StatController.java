package me.ahoo.cosky.rest.controller;

import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.cosky.rest.dto.GetStatResponse;
import me.ahoo.cosky.rest.support.RequestPathPrefix;
import me.ahoo.cosky.config.ConfigService;
import me.ahoo.cosky.core.NamespaceService;
import me.ahoo.cosky.discovery.ServiceDiscovery;
import me.ahoo.cosky.discovery.ServiceStatistic;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
@CrossOrigin("*")
@RestController
@RequestMapping(RequestPathPrefix.STAT_PREFIX)
@Slf4j
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
        var getNamespacesFuture = namespaceService.getNamespaces();
        var getServicesFuture = serviceDiscovery.getServices(namespace);
        var getInstanceCountFuture = serviceStatistic.getInstanceCount(namespace);
        var getConfigsFuture = configService.getConfigs(namespace);
        return CompletableFuture.allOf(getNamespacesFuture, getServicesFuture, getInstanceCountFuture, getConfigsFuture).thenApply((nil) -> {
            var statResponse = new GetStatResponse();
            statResponse.setNamespaces(getNamespacesFuture.join().size());
            statResponse.setServices(getServicesFuture.join().size());
            statResponse.setInstances(getInstanceCountFuture.join().intValue());
            statResponse.setConfigs(getConfigsFuture.join().size());
            return statResponse;
        });
    }
}
