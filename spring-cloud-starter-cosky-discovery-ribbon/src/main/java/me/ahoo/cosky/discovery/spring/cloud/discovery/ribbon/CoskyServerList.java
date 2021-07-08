/*
 *
 *  * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package me.ahoo.cosky.discovery.spring.cloud.discovery.ribbon;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.AbstractServerList;
import lombok.var;
import me.ahoo.cosky.core.util.Futures;
import me.ahoo.cosky.discovery.ServiceDiscovery;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author ahoo wang
 */
public class CoskyServerList extends AbstractServerList<CoskyServer> {
    private String serviceId;
    private final ServiceDiscovery serviceDiscovery;

    public CoskyServerList(ServiceDiscovery serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
    }

    @Override
    public void initWithNiwsConfig(IClientConfig iClientConfig) {
        this.serviceId = iClientConfig.getClientName();
    }

    @Override
    public List<CoskyServer> getInitialListOfServers() {
        return getCoskyServers();
    }

    private List<CoskyServer> getCoskyServers() {
        var getInstancesFuture = serviceDiscovery.getInstances(this.serviceId);
        var instances = Futures.getUnChecked(getInstancesFuture, Duration.ofSeconds(2));
        if (instances.isEmpty()) {
            Collections.emptyList();
        }
        return instances.stream().map(CoskyServer::new).collect(Collectors.toList());
    }

    /**
     * Return updated list of servers. This is called say every 30 secs
     * (configurable) by the Loadbalancer's Ping cycle
     */
    @Override
    public List<CoskyServer> getUpdatedListOfServers() {
        return getCoskyServers();
    }
}
