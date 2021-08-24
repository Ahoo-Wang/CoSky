/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    public CompletableFuture<List<String>> getServices(@PathVariable String namespace) {
        return discoveryService.getServices(namespace).toFuture();
    }

    @PutMapping(RequestPathPrefix.SERVICES_SERVICE)
    public CompletableFuture<Boolean> setService(@PathVariable String namespace, @PathVariable String serviceId) {
        return serviceRegistry.setService(namespace, serviceId).toFuture();
    }

    @DeleteMapping(RequestPathPrefix.SERVICES_SERVICE)
    public CompletableFuture<Boolean> removeService(@PathVariable String namespace, @PathVariable String serviceId) {
        return serviceRegistry.removeService(namespace, serviceId).toFuture();
    }

    @GetMapping(RequestPathPrefix.SERVICES_INSTANCES)
    public CompletableFuture<List<ServiceInstance>> getInstances(@PathVariable String namespace, @PathVariable String serviceId) {
        return discoveryService.getInstances(namespace, serviceId).toFuture();
    }

    @PutMapping(RequestPathPrefix.SERVICES_INSTANCES)
    public CompletableFuture<Boolean> register(@PathVariable String namespace, @PathVariable String serviceId, @RequestBody ServiceInstance instance) {
        instance.setServiceId(serviceId);
        var instanceId = InstanceIdGenerator.DEFAULT.generate(instance);
        instance.setInstanceId(instanceId);
        return serviceRegistry.register(namespace, instance).toFuture();
    }

    @DeleteMapping(RequestPathPrefix.SERVICES_INSTANCES_INSTANCE)
    public CompletableFuture<Boolean> deregister(@PathVariable String namespace, @PathVariable String serviceId, @PathVariable String instanceId) {
        return serviceRegistry.deregister(namespace, serviceId, instanceId).toFuture();
    }

    @PutMapping(RequestPathPrefix.SERVICES_INSTANCES_INSTANCE_METADATA)
    public CompletableFuture<Boolean> setMetadata(@PathVariable String namespace, @PathVariable String serviceId, @PathVariable String instanceId, @RequestBody Map<String, String> metadata) {
        return serviceRegistry.setMetadata(namespace, serviceId, instanceId, metadata).toFuture();
    }

    @GetMapping(RequestPathPrefix.SERVICES_STATS)
    public CompletableFuture<List<ServiceStat>> getServiceStats(@PathVariable String namespace) {
        return serviceStatistic.getServiceStats(namespace).toFuture();
    }

    @GetMapping(RequestPathPrefix.SERVICES_LB)
    public CompletableFuture<ServiceInstance> choose(@PathVariable String namespace, @PathVariable String serviceId) {
        return loadBalancer.choose(namespace, serviceId).toFuture();
    }

}
