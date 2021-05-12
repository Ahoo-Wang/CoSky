package me.ahoo.govern.rest.controller;

import me.ahoo.govern.discovery.*;
import me.ahoo.govern.discovery.loadbalancer.LoadBalancer;
import me.ahoo.govern.rest.support.RequestPathPrefix;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author ahoo wang
 */
@RestController
@RequestMapping(RequestPathPrefix.SERVICES_PREFIX)
public class ServiceController {
    private final ServiceDiscovery discoveryService;
    private final ServiceRegistry serviceRegistry;
    private final ServiceStatistic serviceStatistic;
    private final LoadBalancer loadBalancer;

    public ServiceController(ServiceDiscovery discoveryService, ServiceRegistry serviceRegistry, ServiceStatistic serviceStatistic, LoadBalancer loadBalancer) {
        this.discoveryService = discoveryService;
        this.serviceRegistry = serviceRegistry;
        this.serviceStatistic = serviceStatistic;
        this.loadBalancer = loadBalancer;
    }

    @GetMapping
    public Set<String> getServices(@PathVariable String namespace) {
        return discoveryService.getServices(namespace).join();
    }

    @GetMapping(RequestPathPrefix.SERVICES_INSTANCES)
    public List<ServiceInstance> getInstances(@PathVariable String namespace, @PathVariable String serviceId) {
        return discoveryService.getInstances(namespace, serviceId).join();
    }

    @PutMapping(RequestPathPrefix.SERVICES_INSTANCES)
    public Boolean register(@PathVariable String namespace, @PathVariable String serviceId, @RequestBody ServiceInstance instance) {
        instance.setServiceId(serviceId);
        return serviceRegistry.register(namespace, instance).join();
    }

    @DeleteMapping(RequestPathPrefix.SERVICES_INSTANCES_INSTANCE)
    public Boolean deregister(@PathVariable String namespace, @PathVariable String serviceId, @PathVariable String instanceId) {
        return serviceRegistry.deregister(namespace, serviceId, instanceId).join();
    }

    @PutMapping(RequestPathPrefix.SERVICES_INSTANCES_INSTANCE_METADATA)
    public Boolean setMetadata(@PathVariable String namespace, @PathVariable String serviceId, @PathVariable String instanceId, @RequestBody Map<String, String> metadata) {
        return serviceRegistry.setMetadata(namespace, serviceId, instanceId, metadata).join();
    }

    @GetMapping(RequestPathPrefix.SERVICES_STATS)
    public List<ServiceStat> getServiceStats(@PathVariable String namespace) {
        return serviceStatistic.getServiceStats(namespace).join();
    }

    @GetMapping(RequestPathPrefix.SERVICES_LB)
    public ServiceInstance choose(@PathVariable String namespace, @PathVariable String serviceId) {
        return loadBalancer.choose(namespace, serviceId).join();
    }

}
