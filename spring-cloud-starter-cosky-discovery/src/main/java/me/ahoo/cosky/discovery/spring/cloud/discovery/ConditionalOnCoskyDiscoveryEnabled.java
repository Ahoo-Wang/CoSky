package me.ahoo.cosky.discovery.spring.cloud.discovery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author ahoo wang
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ConditionalOnProperty(value = ConditionalOnCoskyDiscoveryEnabled.ENABLED_KEY, matchIfMissing = true)
public @interface ConditionalOnCoskyDiscoveryEnabled {
    String ENABLED_KEY = CoskyDiscoveryProperties.PREFIX + ".enabled";
}
