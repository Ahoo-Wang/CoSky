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

package me.ahoo.cosky.discovery.spring.cloud.registry;

import me.ahoo.cosky.discovery.RenewInstanceService;
import me.ahoo.cosky.discovery.ServiceInstance;
import me.ahoo.cosky.discovery.spring.cloud.support.StatusConstants;

import org.springframework.cloud.client.serviceregistry.ServiceRegistry;

/**
 * Cosky Service Registry.
 *
 * @author ahoo wang
 */
public class CoskyServiceRegistry implements ServiceRegistry<CoskyRegistration> {
    
    private final me.ahoo.cosky.discovery.ServiceRegistry serviceRegistry;
    private final RenewInstanceService renewInstanceService;
    private final CoskyRegistryProperties coskyRegistryProperties;
    
    public CoskyServiceRegistry(
        me.ahoo.cosky.discovery.ServiceRegistry serviceRegistry,
        RenewInstanceService renewInstanceService, CoskyRegistryProperties coskyRegistryProperties) {
        this.serviceRegistry = serviceRegistry;
        this.renewInstanceService = renewInstanceService;
        this.coskyRegistryProperties = coskyRegistryProperties;
    }
    
    @Override
    public void register(CoskyRegistration registration) {
        ServiceInstance instance = registration.of();
        Boolean succeeded = serviceRegistry.register(instance).block(coskyRegistryProperties.getTimeout());
    
        if (Boolean.FALSE.equals(succeeded)) {
            throw new RuntimeException("Service registration failed");
        }
        renewInstanceService.start();
    }
    
    @Override
    public void deregister(CoskyRegistration registration) {
        ServiceInstance instance = registration.of();
        Boolean succeeded = serviceRegistry.deregister(instance).block(coskyRegistryProperties.getTimeout());
        
        if (Boolean.FALSE.equals(succeeded)) {
            throw new RuntimeException("Service deregister failed");
        }
    }
    
    @Override
    public void close() {
        renewInstanceService.stop();
    }
    
    @Override
    public void setStatus(CoskyRegistration registration, String status) {
        registration.getMetadata().put(StatusConstants.INSTANCE_STATUS_KEY, status);
        ServiceInstance instance = registration.of();
        serviceRegistry
            .setMetadata(instance.getServiceId(), instance.getInstanceId(), StatusConstants.INSTANCE_STATUS_KEY, status)
            .block(coskyRegistryProperties.getTimeout());
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <T> T getStatus(CoskyRegistration registration) {
        return (T) registration.getMetadata().get(StatusConstants.INSTANCE_STATUS_KEY);
    }
}
