package me.ahoo.cosky.rest.controller;

import lombok.var;
import me.ahoo.cosky.discovery.*;
import me.ahoo.cosky.rest.support.RequestPathPrefix;
import me.ahoo.cosky.discovery.loadbalancer.LoadBalancer;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
@CrossOrigin("*")
@RestController
@RequestMapping(RequestPathPrefix.SERVICES_PREFIX)
public class ServiceController {
    private final ServiceDiscovery discoveryService;
    private final ServiceRegistry serviceRegistry;
    private final ServiceStatistic serviceStatistic;
    private final LoadBalancer loadBalancer;

    public ServiceController(ServiceRegistry serviceRegistry,
                             ServiceDiscovery discoveryService,
                             ServiceStatistic serviceStatistic,
                             LoadBalancer loadBalancer) {
        this.discoveryService = discoveryService;
        this.serviceRegistry = serviceRegistry;
        this.serviceStatistic = serviceStatistic;
        this.loadBalancer = loadBalancer;
    }

    @GetMapping
    public CompletableFuture<Set<String>> getServices(@PathVariable String namespace) {
        return discoveryService.getServices(namespace);
    }

    @PutMapping(RequestPathPrefix.SERVICES_SERVICE)
    public CompletableFuture<Boolean> setService(@PathVariable String namespace, @PathVariable String serviceId) {
        return serviceRegistry.setService(namespace, serviceId);
    }

    @DeleteMapping(RequestPathPrefix.SERVICES_SERVICE)
    public CompletableFuture<Boolean> removeService(@PathVariable String namespace, @PathVariable String serviceId) {
        return serviceRegistry.removeService(namespace, serviceId);
    }

    @GetMapping(RequestPathPrefix.SERVICES_INSTANCES)
    public CompletableFuture<List<ServiceInstance>> getInstances(@PathVariable String namespace, @PathVariable String serviceId) {
        return discoveryService.getInstances(namespace, serviceId);
    }

    @PutMapping(RequestPathPrefix.SERVICES_INSTANCES)
    public CompletableFuture<Boolean> register(@PathVariable String namespace, @PathVariable String serviceId, @RequestBody ServiceInstance instance) {
        instance.setServiceId(serviceId);
        var instanceId = InstanceIdGenerator.DEFAULT.generate(instance);
        instance.setInstanceId(instanceId);
        return serviceRegistry.register(namespace, instance);
    }

    @DeleteMapping(RequestPathPrefix.SERVICES_INSTANCES_INSTANCE)
    public CompletableFuture<Boolean> deregister(@PathVariable String namespace, @PathVariable String serviceId, @PathVariable String instanceId) {
        return serviceRegistry.deregister(namespace, serviceId, instanceId);
    }

    @PutMapping(RequestPathPrefix.SERVICES_INSTANCES_INSTANCE_METADATA)
    public CompletableFuture<Boolean> setMetadata(@PathVariable String namespace, @PathVariable String serviceId, @PathVariable String instanceId, @RequestBody Map<String, String> metadata) {
        return serviceRegistry.setMetadata(namespace, serviceId, instanceId, metadata);
    }

    @GetMapping(RequestPathPrefix.SERVICES_STATS)
    public CompletableFuture<List<ServiceStat>> getServiceStats(@PathVariable String namespace) {
        return serviceStatistic.getServiceStats(namespace);
    }

    @GetMapping(RequestPathPrefix.SERVICES_LB)
    public CompletableFuture<ServiceInstance> choose(@PathVariable String namespace, @PathVariable String serviceId) {
        return loadBalancer.choose(namespace, serviceId);
    }

}
