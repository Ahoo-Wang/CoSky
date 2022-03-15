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

import me.ahoo.cosky.discovery.ServiceInstance;

import org.springframework.cloud.client.serviceregistry.Registration;

import java.net.URI;
import java.util.Map;

/**
 * Cosky Registration.
 *
 * @author ahoo wang
 */
public class CoskyRegistration implements Registration {
    
    private final ServiceInstance serviceInstance;
    
    public CoskyRegistration(ServiceInstance serviceInstance) {
        this.serviceInstance = serviceInstance;
    }
    
    @Override
    public String getServiceId() {
        return serviceInstance.getServiceId();
    }
    
    @Override
    public String getHost() {
        return serviceInstance.getHost();
    }
    
    @Override
    public int getPort() {
        return serviceInstance.getPort();
    }
    
    public void setPort(int port) {
        serviceInstance.setPort(port);
    }
    
    @Override
    public boolean isSecure() {
        return serviceInstance.isSecure();
    }
    
    @Override
    public URI getUri() {
        return serviceInstance.parseUri();
    }
    
    @Override
    public Map<String, String> getMetadata() {
        return serviceInstance.getMetadata();
    }
    
    public ServiceInstance of() {
        return serviceInstance;
    }
}
