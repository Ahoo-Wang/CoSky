package me.ahoo.govern.rest.controller;

import lombok.var;
import me.ahoo.govern.config.ConfigService;
import me.ahoo.govern.core.NamespaceService;
import me.ahoo.govern.discovery.ServiceDiscovery;
import me.ahoo.govern.discovery.ServiceStatistic;
import me.ahoo.govern.rest.dto.GetStatResponse;
import me.ahoo.govern.rest.support.RequestPathPrefix;
import org.springframework.web.bind.annotation.*;

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
    public GetStatResponse getStat(@PathVariable String namespace) {
        var statResponse = new GetStatResponse();
        statResponse.setNamespaces(namespaceService.getNamespaces().join().size());
        statResponse.setServices(serviceDiscovery.getServices(namespace).join().size());
        statResponse.setInstances(serviceStatistic.getInstanceCount(namespace).join().intValue());
        statResponse.setConfigs(configService.getConfigs(namespace).join().size());
        return statResponse;
    }
}
