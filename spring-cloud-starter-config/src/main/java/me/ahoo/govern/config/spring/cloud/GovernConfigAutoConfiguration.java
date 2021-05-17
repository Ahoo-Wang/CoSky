package me.ahoo.govern.config.spring.cloud;

import me.ahoo.govern.config.ConfigListenable;
import me.ahoo.govern.config.spring.cloud.refresh.GovernConfigRefresher;
import me.ahoo.govern.spring.cloud.GovernProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
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

    @Bean
    @ConditionalOnMissingBean
    public GovernConfigRefresher governConfigRefresher(GovernProperties governProperties,
                                                       GovernConfigProperties configProperties,
                                                       ConfigListenable configListenable) {
        return new GovernConfigRefresher(governProperties, configProperties, configListenable);
    }
}
