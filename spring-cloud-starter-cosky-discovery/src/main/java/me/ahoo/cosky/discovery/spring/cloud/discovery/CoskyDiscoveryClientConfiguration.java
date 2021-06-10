package me.ahoo.cosky.discovery.spring.cloud.discovery;

import me.ahoo.cosky.discovery.ServiceDiscovery;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.CommonsClientAutoConfiguration;
import org.springframework.cloud.client.ConditionalOnBlockingDiscoveryEnabled;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCoskyDiscoveryEnabled
@ConditionalOnDiscoveryEnabled
@ConditionalOnBlockingDiscoveryEnabled
@AutoConfigureBefore({CommonsClientAutoConfiguration.class, SimpleDiscoveryClientAutoConfiguration.class})
@AutoConfigureAfter(CoskyDiscoveryAutoConfiguration.class)
public class CoskyDiscoveryClientConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CoskyDiscoveryClient coskyDiscoveryClient(
            ServiceDiscovery serviceDiscovery, CoskyDiscoveryProperties coskyDiscoveryProperties) {
        return new CoskyDiscoveryClient(serviceDiscovery, coskyDiscoveryProperties);
    }
}
