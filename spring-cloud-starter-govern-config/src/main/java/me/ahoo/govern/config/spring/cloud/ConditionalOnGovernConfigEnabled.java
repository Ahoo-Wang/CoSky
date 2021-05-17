package me.ahoo.govern.config.spring.cloud;

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
@ConditionalOnProperty(value = ConditionalOnGovernConfigEnabled.ENABLED_KEY, matchIfMissing = true)
public @interface ConditionalOnGovernConfigEnabled {
    String ENABLED_KEY = GovernConfigProperties.PREFIX + ".enabled";
}
