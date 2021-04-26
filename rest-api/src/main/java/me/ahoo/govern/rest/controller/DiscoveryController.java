package me.ahoo.govern.rest.controller;

import me.ahoo.govern.discovery.ServiceDiscovery;
import me.ahoo.govern.discovery.ServiceInstance;
import me.ahoo.govern.discovery.ServiceRegistry;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author ahoo wang
 */
@RestController
@RequestMapping("/v1/services")
public class DiscoveryController {
    private final ServiceDiscovery discoveryService;
    private final ServiceRegistry serviceRegistry;
    private final DiscoveryClient discoveryClient;

    public DiscoveryController(ServiceDiscovery discoveryService, ServiceRegistry serviceRegistry, DiscoveryClient discoveryClient) {
        this.discoveryService = discoveryService;
        this.serviceRegistry = serviceRegistry;
        this.discoveryClient = discoveryClient;
    }

    @GetMapping("/")
    public Set<String> getServices() {
        return discoveryService.getServices().join();
    }

    @GetMapping("/{serviceId}/instances")
    public List<ServiceInstance> getInstances(@PathVariable String serviceId) {
        return discoveryService.getInstances(serviceId).join();
    }

    @PutMapping("/{serviceId}/instances")
    public Boolean register(@PathVariable String serviceId, @RequestBody ServiceInstance instance) {
        instance.setServiceId(serviceId);
        return serviceRegistry.register(instance).join();
    }

    @DeleteMapping("/{serviceId}/instances/{instanceId}")
    public Boolean deregister(@PathVariable String serviceId, @PathVariable String instanceId) {
        return serviceRegistry.deregister(serviceId, instanceId).join();
    }

    @PutMapping("/{serviceId}/instances/{instanceId}/metadata")
    public Boolean setMetadata(@PathVariable String serviceId, @PathVariable String instanceId, @RequestBody Map<String, String> metadata) {
        return serviceRegistry.setMetadata(serviceId, instanceId, metadata).join();
    }

}
