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

package me.ahoo.cosky.rest.dto.stat;

/**
 * Get Stat Response.
 *
 * @author ahoo wang
 */
public class GetStatResponse {
    private int namespaces;
    private Services services;
    private int instances;
    private int configs;
    
    public int getNamespaces() {
        return namespaces;
    }
    
    public void setNamespaces(int namespaces) {
        this.namespaces = namespaces;
    }
    
    public Services getServices() {
        return services;
    }
    
    public void setServices(Services services) {
        this.services = services;
    }
    
    public int getInstances() {
        return instances;
    }
    
    public void setInstances(int instances) {
        this.instances = instances;
    }
    
    public int getConfigs() {
        return configs;
    }
    
    public void setConfigs(int configs) {
        this.configs = configs;
    }
    
    public static class Services {
        private int total;
        private int health;
        
        public int getTotal() {
            return total;
        }
        
        public void setTotal(int total) {
            this.total = total;
        }
        
        public int getHealth() {
            return health;
        }
        
        public void setHealth(int health) {
            this.health = health;
        }
    }
}
