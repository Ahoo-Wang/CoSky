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
