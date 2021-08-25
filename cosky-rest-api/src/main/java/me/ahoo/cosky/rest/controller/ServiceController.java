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

import me.ahoo.cosky.discovery.*;
import me.ahoo.cosky.rest.support.RequestPathPrefix;
import me.ahoo.cosky.discovery.loadbalancer.LoadBalancer;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

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
    public Mono<List<String>> getServices(@PathVariable String namespace) {
        return discoveryService.getServices(namespace);
    }

    @PutMapping(RequestPathPrefix.SERVICES_SERVICE)
    public Mono<Boolean> setService(@PathVariable String namespace, @PathVariable String serviceId) {
        return serviceRegistry.setService(namespace, serviceId);
    }

    @DeleteMapping(RequestPathPrefix.SERVICES_SERVICE)
    public Mono<Boolean> removeService(@PathVariable String namespace, @PathVariable String serviceId) {
        return serviceRegistry.removeService(namespace, serviceId);
    }

    @GetMapping(RequestPathPrefix.SERVICES_INSTANCES)
    public Mono<List<ServiceInstance>> getInstances(@PathVariable String namespace, @PathVariable String serviceId) {
        return discoveryService.getInstances(namespace, serviceId);
    }

    @PutMapping(RequestPathPrefix.SERVICES_INSTANCES)
    public Mono<Boolean> register(@PathVariable String namespace, @PathVariable String serviceId, @RequestBody ServiceInstance instance) {
        instance.setServiceId(serviceId);
        String instanceId = InstanceIdGenerator.DEFAULT.generate(instance);
        instance.setInstanceId(instanceId);
        return serviceRegistry.register(namespace, instance);
    }

    @DeleteMapping(RequestPathPrefix.SERVICES_INSTANCES_INSTANCE)
    public Mono<Boolean> deregister(@PathVariable String namespace, @PathVariable String serviceId, @PathVariable String instanceId) {
        return serviceRegistry.deregister(namespace, serviceId, instanceId);
    }

    @PutMapping(RequestPathPrefix.SERVICES_INSTANCES_INSTANCE_METADATA)
    public Mono<Boolean> setMetadata(@PathVariable String namespace, @PathVariable String serviceId, @PathVariable String instanceId, @RequestBody Map<String, String> metadata) {
        return serviceRegistry.setMetadata(namespace, serviceId, instanceId, metadata);
    }

    @GetMapping(RequestPathPrefix.SERVICES_STATS)
    public Mono<List<ServiceStat>> getServiceStats(@PathVariable String namespace) {
        return serviceStatistic.getServiceStats(namespace);
    }

    @GetMapping(RequestPathPrefix.SERVICES_LB)
    public Mono<ServiceInstance> choose(@PathVariable String namespace, @PathVariable String serviceId) {
        return loadBalancer.choose(namespace, serviceId);
    }

}
