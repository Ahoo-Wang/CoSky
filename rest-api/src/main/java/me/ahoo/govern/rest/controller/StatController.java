package me.ahoo.govern.rest.controller;

import lombok.var;
import me.ahoo.govern.config.ConfigService;
import me.ahoo.govern.core.NamespaceService;
import me.ahoo.govern.discovery.ServiceDiscovery;
import me.ahoo.govern.discovery.ServiceStatistic;
import me.ahoo.govern.rest.support.RequestPathPrefix;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;

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
    public HashMap<String, Integer> getStat(@PathVariable String namespace) {
        var stat = new HashMap<String, Integer>(6);
        stat.put("namespaces", namespaceService.getNamespaces().join().size());
        stat.put("services", serviceDiscovery.getServices(namespace).join().size());
        stat.put("instances", serviceStatistic.getInstanceCount(namespace).join().intValue());
        stat.put("configs", configService.getConfigs(namespace).join().size());
        return stat;
    }
}
