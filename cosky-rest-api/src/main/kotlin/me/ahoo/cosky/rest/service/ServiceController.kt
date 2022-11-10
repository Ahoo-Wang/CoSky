/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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
package me.ahoo.cosky.rest.service

import me.ahoo.cosky.discovery.ServiceDiscovery
import me.ahoo.cosky.discovery.ServiceInstance
import me.ahoo.cosky.discovery.ServiceRegistry
import me.ahoo.cosky.discovery.ServiceStat
import me.ahoo.cosky.discovery.ServiceStatistic
import me.ahoo.cosky.discovery.loadbalancer.LoadBalancer
import me.ahoo.cosky.rest.support.RequestPathPrefix
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * Service Controller.
 *
 * @author ahoo wang
 */
@CrossOrigin("*")
@RestController
@RequestMapping(RequestPathPrefix.SERVICES_PREFIX)
class ServiceController(
    private val serviceRegistry: ServiceRegistry,
    private val discoveryService: ServiceDiscovery,
    private val serviceStatistic: ServiceStatistic,
    private val loadBalancer: LoadBalancer
) {
    @GetMapping
    fun getServices(@PathVariable namespace: String): Mono<List<String>> {
        return discoveryService.getServices(namespace).collectList()
    }

    @PutMapping(RequestPathPrefix.SERVICES_SERVICE)
    fun setService(@PathVariable namespace: String, @PathVariable serviceId: String): Mono<Boolean> {
        return serviceRegistry.setService(namespace, serviceId)
    }

    @DeleteMapping(RequestPathPrefix.SERVICES_SERVICE)
    fun removeService(@PathVariable namespace: String, @PathVariable serviceId: String): Mono<Boolean> {
        return serviceRegistry.removeService(namespace, serviceId)
    }

    @GetMapping(RequestPathPrefix.SERVICES_INSTANCES)
    fun getInstances(@PathVariable namespace: String, @PathVariable serviceId: String): Mono<List<ServiceInstance>> {
        return discoveryService.getInstances(namespace, serviceId).collectList()
    }

    @PutMapping(RequestPathPrefix.SERVICES_INSTANCES)
    fun register(
        @PathVariable namespace: String,
        @PathVariable serviceId: String,
        @RequestBody instanceDto: InstanceDto
    ): Mono<Boolean> {
        return serviceRegistry.register(namespace, instanceDto.asServiceInstance(serviceId))
    }

    @DeleteMapping(RequestPathPrefix.SERVICES_INSTANCES_INSTANCE)
    fun deregister(
        @PathVariable namespace: String,
        @PathVariable serviceId: String,
        @PathVariable instanceId: String
    ): Mono<Boolean> {
        return serviceRegistry.deregister(namespace, serviceId, instanceId)
    }

    @PutMapping(RequestPathPrefix.SERVICES_INSTANCES_INSTANCE_METADATA)
    fun setMetadata(
        @PathVariable namespace: String,
        @PathVariable serviceId: String,
        @PathVariable instanceId: String,
        @RequestBody metadata: Map<String, String>
    ): Mono<Boolean> {
        return serviceRegistry.setMetadata(namespace, serviceId, instanceId, metadata)
    }

    @GetMapping(RequestPathPrefix.SERVICES_STATS)
    fun getServiceStats(@PathVariable namespace: String): Mono<List<ServiceStat>> {
        return serviceStatistic.getServiceStats(namespace).collectList()
    }

    @GetMapping(RequestPathPrefix.SERVICES_LB)
    fun choose(@PathVariable namespace: String, @PathVariable serviceId: String): Mono<ServiceInstance> {
        return loadBalancer.choose(namespace, serviceId)
    }
}
