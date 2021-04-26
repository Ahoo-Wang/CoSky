package me.ahoo.govern.config.spring.cloud;

import org.springframework.context.annotation.Configuration;

/**
 *
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnGovernConfigEnabled
public class GovernConfigAutoConfiguration {

    public GovernConfigAutoConfiguration() {
    }
}
