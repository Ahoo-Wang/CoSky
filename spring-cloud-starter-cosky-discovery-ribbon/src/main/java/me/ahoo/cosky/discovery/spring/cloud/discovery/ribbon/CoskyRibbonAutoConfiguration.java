package me.ahoo.cosky.discovery.spring.cloud.discovery.ribbon;

import me.ahoo.cosky.discovery.spring.cloud.discovery.ConditionalOnCoskyDiscoveryEnabled;
import me.ahoo.cosky.discovery.spring.cloud.discovery.CoskyDiscoveryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cloud.netflix.ribbon.RibbonAutoConfiguration;
import org.springframework.cloud.netflix.ribbon.RibbonClients;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;
import org.springframework.context.annotation.Configuration;

/**
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(SpringClientFactory.class)
@ConditionalOnCoskyRibbon
@ConditionalOnCoskyDiscoveryEnabled
@AutoConfigureAfter({RibbonAutoConfiguration.class, CoskyDiscoveryAutoConfiguration.class})
@RibbonClients(defaultConfiguration = CoskyRibbonClientConfiguration.class)
public class CoskyRibbonAutoConfiguration {
}
