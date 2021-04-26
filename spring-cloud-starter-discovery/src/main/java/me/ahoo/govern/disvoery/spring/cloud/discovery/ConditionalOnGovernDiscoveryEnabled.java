package me.ahoo.govern.disvoery.spring.cloud.discovery;

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
@ConditionalOnProperty(value = ConditionalOnGovernDiscoveryEnabled.ENABLED_KEY, matchIfMissing = true)
public @interface ConditionalOnGovernDiscoveryEnabled {
    String ENABLED_KEY = GovernDiscoveryProperties.PREFIX + ".enabled";
}
