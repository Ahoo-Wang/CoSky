package me.ahoo.govern.rest.controller;

import me.ahoo.govern.discovery.ServiceDiscovery;
import me.ahoo.govern.discovery.ServiceInstance;
import me.ahoo.govern.discovery.ServiceRegistry;
import me.ahoo.govern.rest.support.RequestPathPrefix;
import org.springframework.cloud.client.discovery.DiscoveryClient;
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
    private final DiscoveryClient discoveryClient;

    public ServiceController(ServiceDiscovery discoveryService, ServiceRegistry serviceRegistry, DiscoveryClient discoveryClient) {
        this.discoveryService = discoveryService;
        this.serviceRegistry = serviceRegistry;
        this.discoveryClient = discoveryClient;
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

}
