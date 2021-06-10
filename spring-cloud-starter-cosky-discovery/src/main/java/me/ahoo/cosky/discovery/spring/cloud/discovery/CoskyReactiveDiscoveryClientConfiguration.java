package me.ahoo.cosky.discovery.spring.cloud.discovery;

import me.ahoo.cosky.discovery.ServiceDiscovery;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.*;
import org.springframework.cloud.client.discovery.composite.reactive.ReactiveCompositeDiscoveryClientAutoConfiguration;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCoskyDiscoveryEnabled
@ConditionalOnDiscoveryEnabled
@ConditionalOnReactiveDiscoveryEnabled
@AutoConfigureBefore({ReactiveCommonsClientAutoConfiguration.class})
@AutoConfigureAfter({CoskyDiscoveryAutoConfiguration.class, ReactiveCompositeDiscoveryClientAutoConfiguration.class})
public class CoskyReactiveDiscoveryClientConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CoskyReactiveDiscoveryClient coskyReactiveDiscoveryClient(ServiceDiscovery serviceDiscovery) {
        return new CoskyReactiveDiscoveryClient(serviceDiscovery);
    }
}
