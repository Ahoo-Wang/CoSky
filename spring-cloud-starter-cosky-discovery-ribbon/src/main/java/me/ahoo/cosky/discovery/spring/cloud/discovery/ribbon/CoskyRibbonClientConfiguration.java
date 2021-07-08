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

package me.ahoo.cosky.discovery.spring.cloud.discovery.ribbon;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ServerList;
import lombok.var;
import me.ahoo.cosky.discovery.ServiceDiscovery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.netflix.ribbon.PropertiesFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCoskyRibbon
public class CoskyRibbonClientConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ServerList<?> ribbonServerList(PropertiesFactory propertiesFactory, IClientConfig config, ServiceDiscovery serviceDiscovery) {

        if (propertiesFactory.isSet(ServerList.class, config.getClientName())) {
            ServerList serverList = propertiesFactory.get(ServerList.class, config,
                    config.getClientName());
            return serverList;
        }
        var serverList = new CoskyServerList(serviceDiscovery);
        serverList.initWithNiwsConfig(config);
        return serverList;
    }

    @Bean
    @ConditionalOnMissingBean
    public CoskyServerIntrospector coskyServerIntrospector() {
        return new CoskyServerIntrospector();
    }
}
