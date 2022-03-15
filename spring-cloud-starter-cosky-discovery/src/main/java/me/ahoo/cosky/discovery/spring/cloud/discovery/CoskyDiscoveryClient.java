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

package me.ahoo.cosky.discovery.spring.cloud.discovery;

import me.ahoo.cosky.discovery.ServiceDiscovery;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Cosky Discovery Client.
 *
 * @author ahoo wang
 */
public class CoskyDiscoveryClient implements DiscoveryClient {
    private final ServiceDiscovery serviceDiscovery;
    private final CoskyDiscoveryProperties coskyDiscoveryProperties;
    
    public CoskyDiscoveryClient(ServiceDiscovery serviceDiscovery, CoskyDiscoveryProperties coskyDiscoveryProperties) {
        this.serviceDiscovery = serviceDiscovery;
        this.coskyDiscoveryProperties = coskyDiscoveryProperties;
    }
    
    @Override
    public String description() {
        return "CoSky Discovery Client";
    }
    
    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        return serviceDiscovery.getInstances(serviceId)
            .map(serviceInstance -> (ServiceInstance) new CoskyServiceInstance(serviceInstance))
            .collectList()
            .block(coskyDiscoveryProperties.getTimeout())
            ;
    }
    
    @Override
    public List<String> getServices() {
        return serviceDiscovery.getServices().collectList().block(coskyDiscoveryProperties.getTimeout());
    }
    
    @Override
    public int getOrder() {
        return coskyDiscoveryProperties.getOrder();
    }
}
