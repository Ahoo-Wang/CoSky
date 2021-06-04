package me.ahoo.cosky.config.spring.cloud;

import me.ahoo.cosky.config.spring.cloud.refresh.CoskyConfigRefresher;
import me.ahoo.cosky.config.ConfigListenable;
import me.ahoo.cosky.spring.cloud.CoskyProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCoskyConfigEnabled
public class CoskyConfigAutoConfiguration {

    public CoskyConfigAutoConfiguration() {
    }

    @Bean
    @ConditionalOnMissingBean
    public CoskyConfigRefresher governConfigRefresher(CoskyProperties coskyProperties,
                                                      CoskyConfigProperties configProperties,
                                                      ConfigListenable configListenable) {
        return new CoskyConfigRefresher(coskyProperties, configProperties, configListenable);
    }
}
